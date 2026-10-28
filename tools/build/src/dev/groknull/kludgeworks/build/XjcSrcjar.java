// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * usage: XjcSrcjar <out.srcjar> <xjc args…> — runs xjc in-process into a temp dir and zips the
 * sources
 */
public final class XjcSrcjar {
  public static void main(String[] args) throws Exception {
    var out = Path.of(args[0]);
    var tmp = Files.createTempDirectory("xjc");
    var xjcArgs = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
    xjcArgs.addAll(0, java.util.List.of("-d", tmp.toString(), "-npa", "-no-header", "-quiet"));
    int rc = com.sun.tools.xjc.Driver.run(xjcArgs.toArray(String[]::new), System.out, System.err);
    if (rc != 0) System.exit(rc);
    Srcjars.zip(tmp, out, ".java");
  }
}
