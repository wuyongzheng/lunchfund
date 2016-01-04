#!/bin/bash
# purpose: switch package name between <pkg> and <pkg>test
# warning: make sure you have revision control in the case of corruption

pkg=com.wyz.lunchfund

if [ \! -f AndroidManifest.xml ] ; then
	echo "file AndroidManifest.xml not found"
	exit
fi

if grep -q \"$pkg\" AndroidManifest.xml ; then
	srcpkg=$pkg
	dstpkg=${pkg}test
elif grep -q \"${pkg}test\" AndroidManifest.xml ; then
	srcpkg=${pkg}test
	dstpkg=$pkg
else
	echo "package name is neither $pkg nor ${pkg}test"
	exit
fi

sed -i -s s/\"$srcpkg\"/\"$dstpkg\"/g AndroidManifest.xml
find src -name '*.java' | xargs sed -i -s 's/^package '$srcpkg';$/package '$dstpkg';/g'

srcdir=src/`tr . / <<< $srcpkg`
dstdir=src/`tr . / <<< $dstpkg`
mv $srcdir $dstdir
