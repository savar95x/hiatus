#!/usr/bin/env bash
# Wait for the Screenshots workflow on HEAD (a non-main branch) and download its PNGs into out/shots/.
# On failure prints the errors (compile errors or failing screenshot tests).
set -uo pipefail
sha=$(git rev-parse HEAD)
echo "Waiting for Screenshots on ${sha:0:7}…"
id=""
for i in $(seq 1 120); do
  row=$(gh run list --workflow screenshots.yml --commit "$sha" --limit 1 --json databaseId,status,conclusion \
        --jq '.[0] | "\(.databaseId) \(.status) \(.conclusion)"' 2>/dev/null)
  if [ -z "$row" ] || [ "$row" = "null null null" ]; then sleep 10; continue; fi
  read -r id status conclusion <<<"$row"
  if [ "$status" = "completed" ]; then
    echo "Run $id: $conclusion"
    rm -rf out/shots && mkdir -p out/shots
    git fetch -q --depth 1 origin shots 2>/dev/null && git archive FETCH_HEAD | tar -x -C out/shots
    [ "$(cat out/shots/SHA 2>/dev/null)" = "$sha" ] || echo "(shots branch is from another commit)"
    find out/shots -name '*.png' | sort | sed 's|^|  |'
    if [ "$conclusion" != "success" ]; then
      gh run view "$id" --log-failed 2>/dev/null | sed 's/^.*Z //' \
        | grep -E '^e: |What went wrong|Could not|FAILED|Exception|Caused by|at dev\.savar' | head -40
      exit 1
    fi
    exit 0
  fi
  sleep 15
done
echo "Timed out waiting for run $id"; exit 1
