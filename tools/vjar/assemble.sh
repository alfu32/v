#!/usr/bin/env bash
set -euo pipefail

native_root=${1:?native artifact root is required}
tcc_jar=${2:-}
if test -n "$tcc_jar" && test -f "$tcc_jar"; then
	tcc_jar=$(realpath "$tcc_jar")
else
	tcc_jar=''
fi
output=${3:-v.jar}
targets=(linux-x86_64 linux-aarch64 macos-x86_64 macos-aarch64 windows-x86_64 windows-aarch64)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

classes="$work/classes"
resources="$work/resources"
mkdir -p "$classes" "$resources/native" "$resources/vroot/tcc"
native_count=0

for target in "${targets[@]}"; do
	case "$target" in
		windows-*) suffix=.dll ;;
		macos-*) suffix=.dylib ;;
		*) suffix=.so ;;
	esac
	source="$native_root/$target"
	native_v="$source/v$suffix"
	if test -f "$native_v" && test -f "$source/v_jni$suffix"; then
		mkdir -p "$resources/native/$target"
		cp "$native_v" "$resources/native/$target/v$suffix"
		cp "$source/v_jni$suffix" "$resources/native/$target/v_jni$suffix"
		native_count=$((native_count + 1))
		echo "including native payload: $target"
	else
		echo "warning: incomplete native payload for $target; skipping it" >&2
	fi

	libgc=''
	if test -f "$source/libgc.a"; then
		libgc="$source/libgc.a"
	fi

	if test -n "$tcc_jar"; then
		mkdir -p "$work/tcc/$target"
		(
			cd "$work/tcc/$target"
			jar xf "$tcc_jar" "native/$target/tinycc"
		)
		if test -d "$work/tcc/$target/native/$target/tinycc"; then
			cp -R "$work/tcc/$target/native/$target/tinycc" "$resources/vroot/tcc/$target"
		else
			echo "warning: TCC JAR has no payload for $target" >&2
		fi
	fi
	if test -z "$libgc" && test -d "$work/tcc/$target"; then
		libgc=$(find "$work/tcc/$target" -type f -name libgc.a -print -quit)
	fi
	if test -n "$libgc" && test -f "$libgc"; then
		mkdir -p "$resources/vroot/tcc/$target"
		cp "$libgc" "$resources/vroot/tcc/$target/libgc.a"
		echo "including libgc.a: $target"
	else
		echo "warning: no libgc.a payload for $target" >&2
	fi
	done

if test "$native_count" -eq 0; then
	echo 'error: no complete native V payloads were found; no JAR was assembled' >&2
	exit 2
fi

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
