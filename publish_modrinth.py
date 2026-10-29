#!/usr/bin/env python3
"""Build all LuaHUD targets and publish them using only Python's standard library."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parent
API = "https://api.modrinth.com/v2"


def modrinth_token():
    token = os.environ.get("MODRINTH_TOKEN", "").strip()
    if token:
        return token
    env_file = ROOT / ".env"
    if not env_file.is_file():
        return ""
    for line in env_file.read_text(encoding="utf-8-sig").splitlines():
        key, separator, value = line.strip().removeprefix("export ").partition("=")
        if separator and key.strip() == "MODRINTH_TOKEN":
            try:
                values = shlex.split(value, comments=True)
            except ValueError:
                raise ValueError("Invalid MODRINTH_TOKEN quoting in .env") from None
            if len(values) > 1:
                raise ValueError("Invalid MODRINTH_TOKEN value in .env")
            token = values[0].strip() if values else ""
    return token


def properties(path):
    result = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.lstrip().startswith(("#", "!")):
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def game_versions(version_range):
    if re.fullmatch(r"\d+\.\d+(?:\.\d+)?", version_range):
        return [version_range]
    match = re.fullmatch(r">=(\d+\.\d+(?:\.\d+)?) <=(\d+\.\d+(?:\.\d+)?)", version_range)
    if not match:
        raise ValueError(f"Unsupported minecraft_range: {version_range}")
    start, end = [tuple(map(int, value.split("."))) for value in match.groups()]
    if start[:2] != end[:2] or (start + (0,))[:3] > (end + (0,))[:3]:
        raise ValueError(f"Unsupported minecraft_range: {version_range}")
    return [".".join(map(str, start[:2])) + (f".{patch}" if patch else "")
            for patch in range(start[2] if len(start) == 3 else 0,
                               (end[2] if len(end) == 3 else 0) + 1)]


def release_plan():
    version = properties(ROOT / "gradle.properties")["mod_version"]
    changelog_path = ROOT / "changelogs" / f"v{version}.md"
    changelog = changelog_path.read_text(encoding="utf-8")
    if not changelog.strip():
        raise ValueError(f"Empty changelog: {changelog_path}")
    # Use the enabled Stonecutter targets, rather than stale build directories.
    settings = (ROOT / "settings.gradle").read_text(encoding="utf-8")
    match = re.search(r"\bversions\(([^)]*)\)", settings)
    if not match:
        raise ValueError("Cannot find Stonecutter versions in settings.gradle")
    targets = re.findall(r"['\"]([^'\"]+)['\"]", match[1])
    if not targets:
        raise ValueError("No enabled Stonecutter targets")
    plan = []
    for target in targets:
        directory = ROOT / "versions" / target
        props = properties(directory / "gradle.properties")
        jar = directory / "build" / "libs" / f"luahud-{version}+{props['minecraft_version']}.jar"
        games = game_versions(props["minecraft_range"])
        label = games[0] if len(games) == 1 else f"{games[0]}–{games[-1]}"
        plan.append((jar, {
            "name": f"LuaHUD {version} - Minecraft {label}",
            "version_number": jar.stem.removeprefix("luahud-"),
            "changelog": changelog,
            "dependencies": [],
            "game_versions": games,
            "version_type": "beta",
            "loaders": ["fabric"],
            "environment": "client_only",
            "featured": False,
            "status": "listed",
            "file_parts": ["file"],
            "primary_file": "file",
        }))
    return plan


def request(path, token, body=None, content_type=None):
    headers = {"User-Agent": "iFair1/luahud-publisher (https://github.com/iFair1/luahud)",
               "Authorization": token}
    if content_type:
        headers["Content-Type"] = content_type
    req = urllib.request.Request(API + path, data=body, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=120) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Modrinth HTTP {error.code}: {detail}") from error
    except urllib.error.URLError as error:
        # Don't retry POST automatically: the server may have created the version.
        raise RuntimeError(f"Modrinth connection failed: {error.reason}. "
                           "Check published versions before running again.") from error


def multipart(metadata, jar):
    boundary = uuid.uuid4().hex
    body = (
        f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\n'
        'Content-Type: application/json\r\n\r\n'
    ).encode() + json.dumps(metadata).encode("utf-8")
    body += (
        f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; '
        f'filename="{jar.name}"\r\nContent-Type: application/java-archive\r\n\r\n'
    ).encode() + jar.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
    return body, f"multipart/form-data; boundary={boundary}"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="Build and preview without API calls or uploads")
    parser.add_argument("--skip-build", action="store_true", help="Use existing build outputs")
    args = parser.parse_args()
    token = modrinth_token()
    if not args.dry_run and not token:
        parser.error("Set MODRINTH_TOKEN in .env or the environment to a token with VERSION_CREATE permission")
    plan = release_plan()  # Validate configuration and changelog before building.
    if not args.skip_build:
        wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
        subprocess.run([str(wrapper), "buildAll"], cwd=ROOT, check=True)
    for jar, metadata in plan:
        if not jar.is_file():
            raise FileNotFoundError(f"Missing build output: {jar}")
        print(f"{jar.name}: {metadata['name']} | beta | fabric | client_only", flush=True)
    if args.dry_run:
        print(f"Changelog: changelogs/v{properties(ROOT / 'gradle.properties')['mod_version']}.md")
        print("Dry run complete; nothing uploaded.")
        return
    project = request("/project/luahud", token)
    existing = request(f"/project/{project['id']}/version", token)
    pending = []
    # Check every target before publishing any of them. Identical uploads are
    # skipped so a partially completed run can be resumed safely.
    for jar, metadata in plan:
        matches = [v for v in existing if v["version_number"] == metadata["version_number"]]
        if matches:
            digest = hashlib.sha512(jar.read_bytes()).hexdigest()
            if len(matches) != 1 or not any(
                f["filename"] == jar.name and f["hashes"].get("sha512") == digest
                for f in matches[0]["files"]
            ):
                raise ValueError(f"Version already exists with different files: {metadata['version_number']}")
            print(f"Already uploaded, skipping: {jar.name}", flush=True)
        else:
            metadata["project_id"] = project["id"]
            pending.append((jar, metadata))
    for jar, metadata in pending:
        body, content_type = multipart(metadata, jar)
        result = request("/version", token, body, content_type)
        print(f"Published: https://modrinth.com/mod/luahud/version/{result['id']}", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, KeyError, subprocess.CalledProcessError) as error:
        print(f"Error: {error}", file=sys.stderr)
        sys.exit(1)
