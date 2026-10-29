#!/usr/bin/env bash
# Genera APK mínimos (solo manifiesto) para los tests del lector de splits.
# Uso: AAPT2=<ruta a aapt2> ANDROID_JAR=<ruta a android.jar> ./gen-fixtures.sh
set -euo pipefail
: "${AAPT2:?}" "${ANDROID_JAR:?}"
OUT="$(cd "$(dirname "$0")/../resources/apks" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# gen <fichero> <paquete> <versionCode> <atributos extra del manifest>
gen() {
  local name="$1" pkg="$2" ver="$3" extra="$4"
  cat > "$TMP/m.xml" <<XML
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$pkg" android:versionCode="$ver" $extra><application/></manifest>
XML
  "$AAPT2" link -o "$OUT/$name" --manifest "$TMP/m.xml" -I "$ANDROID_JAR"
}

gen base.apk com.example.fx 7 'android:isolatedSplits="true"'
gen base_plain.apk com.example.fx 7 ''
gen config_arm64.apk com.example.fx 7 'split="config.arm64_v8a" configForSplit=""'
gen config_xxhdpi.apk com.example.fx 7 'split="config.xxhdpi" configForSplit=""'
gen feature_x.apk com.example.fx 7 'split="feature_x" android:isFeatureSplit="true" android:isSplitRequired="true"'
gen config_feature_x_es.apk com.example.fx 7 'split="feature_x.config.es" configForSplit="feature_x"'
gen other_version.apk com.example.fx 8 'split="config.en" configForSplit=""'
gen other_package.apk com.example.other 7 'split="config.en" configForSplit=""'
