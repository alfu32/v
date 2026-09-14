#!/usr/bin/env bash
set -euo pipefail

native_root=${1:?native artifact root is required}
tcc_jar=$(realpath "${2:?tcc-cli.jar is required}")
output=${3:-v.jar}
targets=(linux-x86_64 linux-aarch64 macos-x86_64 macos-aarch64 windows-x86_64 windows-aarch64)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

classes="$work/classes"
resources="$work/resources"
mkdir -p "$classes" "$resources/native" "$resources/vroot/tcc"

for target in "${targets[@]}"; do
	case "$target" in
		windows-*) suffix=.dll ;;
		macos-*) suffix=.dylib ;;
		*) suffix=.so ;;
	esac
	source="$native_root/$target"
	native_v="$source/v"
	if [[ "$suffix" == .dll ]]; then native_v="$source/v.dll"; fi
	test -f "$native_v" || { echo "missing V payload for $target" >&2; exit 2; }
	test -f "$source/v_jni$suffix" || { echo "missing JNI payload for $target" >&2; exit 2; }
	mkdir -p "$resources/native/$target"
	cp "$native_v" "$resources/native/$target/v$suffix"
	cp "$source/v_jni$suffix" "$resources/native/$target/v_jni$suffix"

	mkdir -p "$work/tcc/$target"
	(
		cd "$work/tcc/$target"
		jar xf "$tcc_jar" "native/$target/tinycc"
	)
	test -d "$work/tcc/$target/native/$target/tinycc" \
		|| { echo "TCC JAR has no payload for $target" >&2; exit 2; }
	cp -R "$work/tcc/$target/native/$target/tinycc" "$resources/vroot/tcc/$target"
done

cp -R vlib "$resources/vroot/"
for directory in cmd thirdparty; do
	if test -d "$directory"; then
		cp -R "$directory" "$resources/vroot/"
	fi
done
rm -rf "$resources/vroot/thirdparty/tcc"

find tools/vjar/src/main/java -name '*.java' -print0 \
	| xargs -0 javac --release 21 -d "$classes"
mkdir -p "$(dirname "$output")"
jar --create --file "$output" --main-class org.vlang.cli.Main \
	-C "$classes" . -C "$resources" .
echo "created $output"
