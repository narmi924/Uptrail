#!/bin/sh
# Collects the static files (stylesheets, scripts, fonts, images, Bootstrap) that Vercel serves from its CDN in
# front of the demo container, so a browser's parallel asset requests never reach the container.
# Run from the repository root; the result is written to deploy/demo/public/.
set -eu

OUT=deploy/demo/public
BOOTSTRAP_VERSION=$(sed -n 's:.*<bootstrap.version>\(.*\)</bootstrap.version>.*:\1:p' pom.xml)
WORK=$(mktemp -d)

rm -rf "$OUT"
mkdir -p "$OUT/webjars/bootstrap/$BOOTSTRAP_VERSION"
cp -R src/main/resources/static/. "$OUT/"

# The application serves Bootstrap from its WebJar; the same files come from the npm package here.
(cd "$WORK" && npm pack "bootstrap@$BOOTSTRAP_VERSION" --silent >/dev/null && tar -xzf "bootstrap-$BOOTSTRAP_VERSION.tgz")
cp -R "$WORK/package/dist" "$OUT/webjars/bootstrap/$BOOTSTRAP_VERSION/dist"
rm -rf "$WORK"

echo "Static assets collected in $OUT (Bootstrap $BOOTSTRAP_VERSION)"
