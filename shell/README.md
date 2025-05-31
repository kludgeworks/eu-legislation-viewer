# EU Legislation Shell CLI

A command-line interface for retrieving and displaying EU legislation information from the Cellar API.

## Features

- Retrieve legislation work information by CELEX number
- Display title, date of entry into force, amendments, and consolidations
- Support for multiple languages

## Building

To build the CLI module:

```bash
./gradlew :shell:build
```

## Running

### Using Gradle

```bash
./gradlew :shell:bootRun -Dspring-boot.run.arguments="="work 32015R1998"
```

### Using the built JAR

```bash
java -jar shell/build/libs/shell-*.jar work 32015R1998
```

## Usage

```
Usage: work [-hlV] [-l=<languageCode>] <resourceId>

Retrieves and displays Work information from EU Cellar

Parameters:
      <resourceId>          The resource ID (e.g., CELEX number) to retrieve

Options:
  -h, --help                Show this help message and exit.
  -l, --language=<languageCode>
                            Language code for the title (default: eng)
  -V, --version             Print version information and exit.
```

## Examples

Retrieve work information for a regulation in English (default):
```bash
./gradlew :shell:bootRun --args="work 32015R1998"
```

Retrieve work information with French title:
```bash
./gradlew :shell:bootRun --args="work 32015R1998 -l fra"
```

## Output Format

The CLI displays:
- **Title**: The title of the legislation in the specified language
- **Date came into force**: When the legislation became effective
- **Amendments**: List of amendments with CELEX numbers and dates
- **Consolidations**: List of consolidations with CELEX numbers and dates

Example output:
```
Fetching work information for: 32015R1998
============================================================
Title: Commission Regulation (EU) 2015/1998 of 5 November 2015...

Date came into force: 01/02/2016

Amendments:
  - 32017R0815 - 15/05/2017
  - 32019R0103 - 02/02/2019

Consolidations:
  - 02015R1998-20160201 - 01/02/2016
  - 02015R1998-20170515 - 15/05/2017
```
