///usr/bin/env jbang -Djava.rmi.server.hostname=127.0.0.1 "$0" "$@" ; exit $?
//JAVA 25+
//DEPS org.duckdb:duckdb_jdbc:1.4.1.0
//DEPS info.picocli:picocli:4.7.7
//DEPS com.fasterxml.woodstox:woodstox-core:7.1.1
//DEPS org.jdbi:jdbi3-core:3.49.6
//DEPS com.github.f4b6a3:uuid-creator:6.1.1
//SOURCES formex/db/XmlFileRepository.java
//SOURCES formex/cache/FileCache.java
//MISE description="Ingest XML files from downloads directory into DuckDB"


import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import formex.db.XmlFileRepository;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Command(name = "ingest", mixinStandardHelpOptions = true,
        description = "Ingest XML files into DuckDB (expects existing xml_files table)")
public class ingest implements Callable<Integer> {

    @Option(names = {"-d", "--database"}, defaultValue = "formex.db")
    String databaseFile;

    @Option(names = {"-p", "--parallelism"}, defaultValue = "100",
            description = "Max number of files processed concurrently")
    int parallelism;

    @Option(names = {"-b", "--batch-size"}, defaultValue = "500",
            description = "Number of rows per JDBC batch")
    int batchSize;

    @Parameters(index = "0", defaultValue = "downloads",
            description = "Directory to scan for XML files")
    String directory;



    public static void main(String[] args) {
        System.exit(new CommandLine(new ingest()).execute(args));
    }

    @Override
    public Integer call() throws Exception {
        Path root = Paths.get(directory);
        if (!Files.exists(root)) {
            System.err.println("Directory does not exist: " + root);
            return 1;
        }

        System.out.println("📥 Starting XML ingestion...");
        
        // Collect all XML file paths
        List<Path> xmlFiles = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root).filter(Files::isRegularFile)
                .filter(p -> p.toString().toLowerCase().endsWith(".xml"))) {
            xmlFiles = files.toList();
        }
        
        System.out.printf("📂 Found %,d XML files%n", xmlFiles.size());
        
        if (xmlFiles.isEmpty()) {
            System.out.println("✅ No files to ingest");
            return 0;
        }
        
        // Use repository to insert files
        XmlFileRepository repository = new XmlFileRepository(databaseFile);
        long inserted = repository.insertFiles(xmlFiles, batchSize);
        
        System.out.printf("✅ Done. Inserted: %,d files%n", inserted);
        return 0;
    }
}
