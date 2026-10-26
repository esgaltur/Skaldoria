<#
.SYNOPSIS
    Writes the release notes for one version: its own CHANGELOG.md section, and nothing else.

.DESCRIPTION
    Both release scripts used to pass the whole CHANGELOG.md as the GitHub release body. By
    1.3.0 that was 40 KB of every version's history, and GitHub lists the downloads *below* the
    body — so the installers looked missing. This takes only the `## [<Version>]` section, up to
    the next `## [` heading, and links the full changelog at the tag.

    Dot-source it and call New-ReleaseNotesFile; it returns the path of a temporary file for
    `gh release create --notes-file`. Fails rather than publishing an empty body when the
    changelog has no section for the version.
#>

function New-ReleaseNotesFile {
    param(
        [Parameter(Mandatory)][string]$ProjectRoot,
        [Parameter(Mandatory)][string]$Version,
        [string]$Repository = 'esgaltur/Skaldoria'
    )

    $changelog = Get-Content -LiteralPath (Join-Path $ProjectRoot 'CHANGELOG.md') -Encoding utf8
    $heading = "## [$Version]"
    $start = -1
    for ($i = 0; $i -lt $changelog.Count; $i++) {
        if ($changelog[$i].StartsWith($heading)) { $start = $i + 1; break }
    }
    if ($start -lt 0) {
        throw "CHANGELOG.md has no '$heading' section; add one before publishing v$Version."
    }

    $end = $changelog.Count
    for ($i = $start; $i -lt $changelog.Count; $i++) {
        if ($changelog[$i].StartsWith('## [')) { $end = $i; break }
    }

    $section = ($changelog[$start..($end - 1)] -join "`n").Trim()
    if ([string]::IsNullOrWhiteSpace($section)) {
        throw "The '$heading' section of CHANGELOG.md is empty."
    }

    $body = @(
        $section
        ''
        "Downloads are under **Assets** below. Full history: [CHANGELOG.md](https://github.com/$Repository/blob/v$Version/CHANGELOG.md)"
    ) -join "`n"

    $path = Join-Path ([IO.Path]::GetTempPath()) "skaldoria-release-notes-v$Version.md"
    Set-Content -LiteralPath $path -Value $body -Encoding utf8
    $path
}
