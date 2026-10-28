"""Renders the web page template (site/) for publishing, filled in for the latest release.

    python3 tools/build-site.py OUT_DIR                      # latest GitHub Release (needs the gh CLI)
    python3 tools/build-site.py --version 1.0.0 OUT_DIR      # preview as if 1.0.0 were the latest

The Pages workflow runs it on every deploy, and the Release workflow deploys after publishing a release, so the page
follows the releases by itself. In site/index.html:
  {{version}} 1.0.0           {{tag}} v1.0.0 (links pinned to the released source, e.g. the guide)
  {{jar}}     powerarmor-1.0.0.jar, powersuits-1.1.0.jar (the download's file name)
  {{java}}    the JDK the release was built with (from its notes; tools/jdk.env if the notes don't say)
  data-since="1.1.0" on an element: greyed out (class "soon") until 1.1.0 is released
  <span data-coming="1.1.0"></span>: becomes a "coming in 1.1" badge until then, and disappears after
So while 1.1 is being made, new features go on the page marked data-since="1.1.0"; releasing 1.1.0 lights them up.
"""
import argparse
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPO = "ajac-zero/power-suits"


def version_tuple(v):
    return tuple(int(x) for x in v.split("."))


def latest_release():
    """(version, release notes) of the latest published release."""
    out = subprocess.run(["gh", "release", "view", "--repo", REPO, "--json", "tagName,body"],
                         capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit(f"no release found ({out.stderr.strip()}); pass --version to preview")
    data = json.loads(out.stdout)
    return data["tagName"].removeprefix("v"), data["body"] or ""


def pinned_java():
    env = dict(line.split("=", 1) for line in (ROOT / "tools/jdk.env").read_text().splitlines()
               if "=" in line and not line.startswith("#"))
    return env["JDK_RELEASE"].removeprefix("jdk-")


def render(html, version, java):
    released = version_tuple(version)
    # The comment explaining the template is for people editing it, not for visitors.
    html = re.sub(r"\s*<!-- A template:.*?-->", "", html, count=1, flags=re.S)
    # the jar's file name: powerarmor-1.0.0.jar (named after the mod id), powersuits-<version>.jar from 1.1 on
    jar = f"{'powerarmor' if released <= (1, 0, 0) else 'powersuits'}-{version}.jar"
    html = html.replace("{{jar}}", jar)
    html = html.replace("{{version}}", version).replace("{{tag}}", f"v{version}").replace("{{java}}", java)

    def since(m):
        tag = m.group(0)
        if version_tuple(m.group(1)) <= released:
            return tag
        if 'class="' in tag:
            return tag.replace('class="', 'class="soon ', 1)
        return tag[:-1] + ' class="soon">'
    html = re.sub(r'<[a-z]+[^>]*\sdata-since="([\d.]+)"[^>]*>', since, html)

    def coming(m):
        v = m.group(1)
        if version_tuple(v) <= released:
            return ""
        short = re.sub(r"\.0$", "", v)  # 1.1.0 -> "1.1"
        return f' <span class="tag">coming in {short}</span>'
    html = re.sub(r'\s*<span data-coming="([\d.]+)"></span>', coming, html)

    left = re.findall(r"\{\{\w+\}\}", html)
    if left:
        sys.exit(f"unknown placeholders in the page: {sorted(set(left))}")
    return html


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--version", help="render as if this were the latest release (preview)")
    args = ap.parse_args()

    if args.version:
        version, notes = args.version, ""
    else:
        version, notes = latest_release()
    m = re.search(r"Temurin ([\w.+-]+)", notes)
    java = m.group(1) if m else pinned_java()

    out = Path(args.out)
    if out.exists():
        shutil.rmtree(out)
    shutil.copytree(ROOT / "site", out)
    page = out / "index.html"
    page.write_text(render(page.read_text(), version, java))
    print(f"site for {version} (Java {java}) -> {out}")


if __name__ == "__main__":
    main()
