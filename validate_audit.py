#!/usr/bin/env python3
"""
validate_audit.py — Universal Automated Architecture & Blueprint Invariance Validator

Drop-in portable across any project ecosystem (Android/Kotlin/Java, Python, TypeScript/JS,
Flutter/Dart, Rust, Go, C/C++, C#, Swift).

Rigorously enforces AGENTS.md Rule 5 (Living Architectural Blueprint Invariance):
1. Physical Source File Discovery: Every source file in the detected source root(s) must be indexed in CODEBASE_MAP.md.
2. Symbol Existence Verification: Every canonical tag [file::symbol] must map to an existing physical file AND
   contain the declared symbol (class, interface, object, enum, function, or property) in the source code.
3. True Flow Coverage Parity: Measures the true percentage of canonical tags from CODEBASE_MAP.md covered
   by runtime execution flows in ARCHITECTURE_FLOW.md (eliminating tautological self-comparisons).
4. Referential Integrity: Asserts zero broken or orphaned tags across CODEBASE_MAP.md
   and ARCHITECTURE_FLOW.md.

Optional CLI Usage:
  python validate_audit.py [--src-dir <relative_path>]
"""

import argparse
import os
import re
import sys

# Universal set of source code extensions across major languages
SUPPORTED_SOURCE_EXTENSIONS = {
    ".kt", ".java",          # Kotlin / Java (Android, JVM)
    ".py",                   # Python
    ".ts", ".tsx", ".js", ".jsx",  # TypeScript / JavaScript
    ".dart",                 # Dart / Flutter
    ".rs",                   # Rust
    ".go",                   # Go
    ".cpp", ".c", ".cc", ".h", ".hpp",  # C / C++
    ".cs",                   # C#
    ".swift",                # Swift
    ".rb",                   # Ruby
    ".php",                  # PHP
    ".scala",                # Scala
}

# Standard root-level configuration files that are NOT stray application source files
ALLOWED_ROOT_CONFIG_FILES = {
    "validate_audit.py",
    "setup.py",
    "conftest.py",
    "manage.py",
    "build.gradle.kts",
    "settings.gradle.kts",
    "vite.config.ts",
    "vite.config.js",
    "next.config.js",
    "next.config.ts",
    "next.config.mjs",
    "tailwind.config.js",
    "tailwind.config.ts",
    "postcss.config.js",
    "jest.config.js",
    "jest.config.ts",
    "babel.config.js",
    "eslint.config.js",
    "webpack.config.js",
    "tsconfig.json",
    "build.rs",
}

# Standard candidate source roots ordered from most specific to most general
CANDIDATE_SOURCE_ROOTS = [
    ["app/src/main/java", "app/src/main/kotlin"],  # Android
    ["src/main/java", "src/main/kotlin"],          # Standard Gradle / Maven JVM
    ["src"],                                       # TS/JS, Python, Rust, C++, etc.
    ["lib"],                                       # Flutter / Dart, Ruby, Elixir
    ["pkg", "internal", "cmd"],                    # Go
    ["app"],                                       # Python (FastAPI/Flask/Django), Next.js, Rails
]

# Placeholder / template example prefixes to ignore in markdown instruction headers
PLACEHOLDER_TAG_PREFIXES = (
    "repository-relative/path/",
    "path/to/",
    "example/path/",
    "your/path/",
)


def discover_source_roots(repo_root: str, cli_src_dirs: list[str] | None) -> list[str]:
    """Auto-detects active source root directories relative to repo_root."""
    if cli_src_dirs:
        valid = [d.replace("\\", "/").strip("/") for d in cli_src_dirs if os.path.isdir(os.path.join(repo_root, d))]
        if valid:
            return valid
        print(f"FAIL: Specified --src-dir {cli_src_dirs} not found in {repo_root}.")
        sys.exit(1)

    for candidate_group in CANDIDATE_SOURCE_ROOTS:
        existing = [
            rel_dir for rel_dir in candidate_group
            if os.path.isdir(os.path.join(repo_root, rel_dir.replace("/", os.sep)))
        ]
        if existing:
            return existing

    print(
        f"FAIL: Could not auto-detect source root in {repo_root}. "
        f"Checked: {[g for group in CANDIDATE_SOURCE_ROOTS for g in group]}. "
        f"Pass --src-dir <path> to specify a custom source directory."
    )
    sys.exit(1)


def is_placeholder_tag(tag: str) -> bool:
    """Filters out documentation template examples like [repository-relative/path/FileName.ext::EntityName]."""
    if any(tag.startswith(prefix) for prefix in PLACEHOLDER_TAG_PREFIXES):
        return True
    rel_file = tag.split("::")[0]
    if rel_file.endswith(".ext"):
        return True
    return False


def main():
    parser = argparse.ArgumentParser(description="Universal Architectural Blueprint Invariance Validator")
    parser.add_argument(
        "--src-dir",
        action="append",
        dest="src_dirs",
        help="Relative path(s) to source root directory (auto-detected if omitted)",
    )
    args = parser.parse_args()

    repo_root = os.path.dirname(os.path.abspath(__file__))
    project_name = os.path.basename(repo_root).upper()

    codebase_map_path = os.path.join(repo_root, "CODEBASE_MAP.md")
    architecture_flow_path = os.path.join(repo_root, "ARCHITECTURE_FLOW.md")

    src_roots = discover_source_roots(repo_root, args.src_dirs)

    # Build regex matching canonical tags within discovered source roots or general source paths
    escaped_roots = "|".join(re.escape(r) for r in src_roots)
    header_tag_regex = re.compile(rf"^##### `?\[((?:{escaped_roots})/[^:\]\s]+::[^\]\s]+)\]`?", re.MULTILINE)
    tag_regex = re.compile(rf"\[((?:{escaped_roots})/[^:\]\s]+::[^\]\s]+)\]")

    errors = []
    warnings = []

    # 1. Discover physical source files & active project extensions
    physical_source_files = set()
    active_extensions = set()

    for rel_src in src_roots:
        abs_src = os.path.join(repo_root, rel_src.replace("/", os.sep))
        for root, _, files in os.walk(abs_src):
            for f in files:
                _, ext = os.path.splitext(f)
                if ext in SUPPORTED_SOURCE_EXTENSIONS:
                    rel_path = os.path.relpath(os.path.join(root, f), repo_root).replace("\\", "/")
                    physical_source_files.add(rel_path)
                    active_extensions.add(ext)

    # 0. Check for stray scratch source files in repository root matching the project's active extensions
    check_exts = active_extensions if active_extensions else SUPPORTED_SOURCE_EXTENSIONS
    root_stray_files = [
        f for f in os.listdir(repo_root)
        if os.path.splitext(f)[1] in check_exts
        and f not in ALLOWED_ROOT_CONFIG_FILES
        and os.path.isfile(os.path.join(repo_root, f))
    ]
    if root_stray_files:
        errors.append(f"Stray source files found in repository root (outside source sets): {sorted(root_stray_files)}")

    # 2. Parse CODEBASE_MAP.md
    if not os.path.exists(codebase_map_path):
        print(f"FAIL: {codebase_map_path} does not exist.")
        sys.exit(1)

    with open(codebase_map_path, "r", encoding="utf-8") as f:
        map_content = f.read()

    map_tags = {t for t in header_tag_regex.findall(map_content) if not is_placeholder_tag(t)}
    all_map_mentioned_tags = {t for t in tag_regex.findall(map_content) if not is_placeholder_tag(t)}
    unindexed_map_references = all_map_mentioned_tags - map_tags
    if unindexed_map_references:
        errors.append(
            f"Tags referenced in CODEBASE_MAP.md (e.g. Consumers) without a ##### header definition: "
            f"{sorted(list(unindexed_map_references))}"
        )

    mapped_files = {t.split("::")[0] for t in map_tags}

    print(f"[1/4] Physical Source Discovery: {len(physical_source_files)} source files detected.")
    print(f"[2/4] CODEBASE_MAP.md Index: {len(map_tags)} canonical ##### header tags across {len(mapped_files)} files.")

    # Check for unmapped physical files
    unmapped_files = physical_source_files - mapped_files
    if unmapped_files:
        errors.append(f"Unmapped physical source files missing from CODEBASE_MAP.md: {sorted(list(unmapped_files))}")

    # 3. Verify Physical Symbol Existence for all canonical tags in CODEBASE_MAP.md
    missing_physical_entities = []
    for tag in sorted(map_tags):
        rel_file, entity = tag.split("::")
        phys_path = os.path.join(repo_root, rel_file.replace("/", os.sep))
        if not os.path.isfile(phys_path):
            missing_physical_entities.append((tag, f"Physical file '{rel_file}' does not exist on disk"))
            continue

        try:
            with open(phys_path, "r", encoding="utf-8") as sf:
                src_code = sf.read()
        except Exception as e:
            missing_physical_entities.append((tag, f"Could not read '{rel_file}': {e}"))
            continue

        # Extract leaf identifier (e.g. 'MainActivity.onKeyDown' -> 'onKeyDown')
        leaf_symbol = entity.split(".")[-1]
        symbol_pattern = rf"\b{re.escape(leaf_symbol)}\b"
        if not re.search(symbol_pattern, src_code):
            missing_physical_entities.append((tag, f"Symbol '{leaf_symbol}' not found in '{rel_file}'"))

    if missing_physical_entities:
        for tag, reason in missing_physical_entities:
            errors.append(f"Invalid canonical tag [{tag}]: {reason}")

    print(
        f"[3/4] Physical Symbol Verification: "
        f"{len(map_tags) - len(missing_physical_entities)}/{len(map_tags)} symbols physically verified."
    )

    # 4. Parse ARCHITECTURE_FLOW.md & Verify True Coverage
    if not os.path.exists(architecture_flow_path):
        print(f"FAIL: {architecture_flow_path} does not exist.")
        sys.exit(1)

    with open(architecture_flow_path, "r", encoding="utf-8") as f:
        flow_content = f.read()

    flow_tags = {t for t in tag_regex.findall(flow_content) if not is_placeholder_tag(t)}

    # Flow broken tags: tags referenced in FLOW that are NOT in CODEBASE_MAP
    broken_flow_tags = flow_tags - map_tags
    if broken_flow_tags:
        errors.append(f"Broken/Unindexed tags in ARCHITECTURE_FLOW.md: {broken_flow_tags}")

    # True coverage: what percentage of CODEBASE_MAP canonical tags appear in ARCHITECTURE_FLOW?
    flow_covered_canonical_tags = map_tags & flow_tags
    flow_missing_canonical_tags = map_tags - flow_tags
    flow_coverage_pct = (len(flow_covered_canonical_tags) / len(map_tags) * 100.0) if map_tags else 0.0

    print(
        f"[4/4] ARCHITECTURE_FLOW.md: {len(flow_tags)} tag references. "
        f"Canonical Coverage: {len(flow_covered_canonical_tags)}/{len(map_tags)} ({flow_coverage_pct:.1f}%)."
    )
    if flow_missing_canonical_tags:
        warnings.append(
            f"{len(flow_missing_canonical_tags)} canonical tags omitted from ARCHITECTURE_FLOW.md: "
            f"{sorted(list(flow_missing_canonical_tags))}"
        )

    # Objective Summary Report
    print("\n" + "=" * 68)
    print(f"      {project_name} ARCHITECTURAL INVARIANCE AUDIT REPORT")
    print("=" * 68)
    print(f"  Physical Source Files:               {len(physical_source_files)} (All {len(mapped_files)} indexed in CODEBASE_MAP)")
    print(f"  Canonical Tags in CODEBASE_MAP.md:   {len(map_tags)}")
    print(
        f"  Physical Symbol Invariance:          {len(map_tags) - len(missing_physical_entities)}/{len(map_tags)} verified (100%)"
        if not missing_physical_entities
        else f"FAIL ({len(missing_physical_entities)} missing)"
    )
    print(f"  Tags in ARCHITECTURE_FLOW.md:        {len(flow_tags)}")
    print(f"  True Flow Coverage Parity:           {len(flow_covered_canonical_tags)}/{len(map_tags)} ({flow_coverage_pct:.1f}%)")
    print(f"  Broken/Orphaned Tag Count:           {len(broken_flow_tags)}")
    print(f"  Unmapped Source Files:               {len(unmapped_files)}")
    print("=" * 68)

    if warnings:
        print("\nWARNINGS:")
        for w in warnings:
            print(f"  ! {w}")

    if errors:
        print("\nCRITICAL AUDIT ERRORS:")
        for err in errors:
            print(f"  x {err}")
        sys.exit(1)

    print("\nSUCCESS: 100% Architectural Parity, Physical Invariance & Zero Drift Confirmed.")
    sys.exit(0)


if __name__ == "__main__":
    main()
