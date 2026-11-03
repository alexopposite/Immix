# Personal fork and first review round

Personal fork: https://github.com/alexopposite/Immix
Original Immix: https://github.com/smerschjohann/Immix
Android TV upstream: https://github.com/giejay/Immich-Android-TV

## Review branches

| Reference | Purpose | Review base |
| --- | --- | --- |
| `main` | Untouched original Immix at `c9dcea6f0b38822fd93050651be23bde9ad9251c` | Original fork baseline |
| `baseline/immix-original` tag | Fixed original baseline, even if main later advances | Same commit as original main |
| `revive/merge-upstream-6.2.0` | Merge Android TV 6.2.0, build fixes, API permission docs, private provisioning tool, initial device trial | `main` |
| `feature/frame-hardware-helper` | Separate hardware helper, client integration, prototype tests and publication hygiene | `revive/merge-upstream-6.2.0` |

The revival branch includes hundreds of upstream commits through a real merge. Use the aggregate Files changed view to see the resulting update, and first-parent history to see our integration commits. The helper comparison isolates our hardware work from that large upstream update. These branches are not merged into main yet.

Comparisons:

- [Revival versus original Immix](https://github.com/alexopposite/Immix/compare/main...revive/merge-upstream-6.2.0)
- [Helper versus revival](https://github.com/alexopposite/Immix/compare/revive/merge-upstream-6.2.0...feature/frame-hardware-helper)
- [All current work versus original Immix](https://github.com/alexopposite/Immix/compare/main...feature/frame-hardware-helper)

## Local remotes

`origin` is the personal fork and is the destination for work branches. `immix-upstream` is the original Immix repository. `upstream` is Android TV. Push explicit branches; avoid `--all`, `--mirror`, or force pushes. Local pre-publication snapshot branches are recovery copies and are not published.

```sh
git log --first-parent --oneline main..feature/frame-hardware-helper
git diff main...revive/merge-upstream-6.2.0
git diff revive/merge-upstream-6.2.0...feature/frame-hardware-helper
```

## Private deployment data

API key files (`*.key`), local Android tooling/caches, private settings backups and trial screenshots remain outside source control. Deployment-specific addresses were replaced in our unpublished history before the first push. The original local snapshots were preserved locally; they were not pushed. Public history should never contain API keys or ADB private keys.

The scripts now use documentation examples (`https://immich.example.com` and `192.0.2.10:5555`). Pass the real `--server` and `--serial` explicitly when performing an approved local trial. Nothing in this repository setup changes the frame, launches Immix, or replaces Nixplay.

Validation carried forward: successful debug/release builds and 150 Android unit tests. The five synthetic provisioning tests were also rerun after replacing the sample defaults. See FRAME-HARDWARE.md for observed device behavior and remaining prototype limitations.
