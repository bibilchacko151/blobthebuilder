# PNR Azure Blob Downloader

This application downloads all Azure Blob Storage files for a given PNR and recreates the full Azure virtual folder structure under a local output directory.

## Prerequisites

- Java 21
- Azure CLI for local authentication, if using `DefaultAzureCredential` on a developer machine
- Azure RBAC permission such as `Storage Blob Data Reader`

## Azure Authentication

Log in locally with Azure CLI:

```bash
az login
```

If needed, select a subscription:

```bash
az account set --subscription "<subscription>"
```

Do not store credentials, keys, or SAS tokens in the application properties files.

## Configuration

The application reads environment-specific settings from:

- `config/application-dev.properties`
- `config/application-prd.properties`

By default, `--env=dev` loads `config/application-dev.properties` and `--env=prd` loads `config/application-prd.properties`.

Example:

`config/application-dev.properties`

```properties
storage-account=my-dev-storage-account
container=my-dev-container
```

`config/application-prd.properties`

```properties
storage-account=my-prd-storage-account
container=my-prd-container
```

Override the file with `--config=<path>` if needed.

## Build

Linux/macOS:

```bash
./gradlew clean build
./gradlew clean shadowJar
```

Windows:

```bat
gradlew.bat clean build
gradlew.bat clean shadowJar
```

The runnable fat JAR is created at:

```text
build/libs/pnr-blob-downloader.jar
```

## Run

Linux/macOS:

```bash
java -jar build/libs/pnr-blob-downloader.jar --env=dev --pnr=ABC123 --output=/home/user/downloads
```

Production:

```bash
java -jar build/libs/pnr-blob-downloader.jar --env=prd --pnr=ABC123 --output=/home/user/downloads
```

Windows:

```bat
java -jar build\libs\pnr-blob-downloader.jar --env=prd --pnr=ABC123 --output="C:\Downloads"
```

Custom config file:

```bash
java -jar build/libs/pnr-blob-downloader.jar --config=/opt/pnr/application-prd.properties --env=prd --pnr=ABC123 --output=/data/downloads
```

## Command-Line Arguments

- `--env=<environment>`: required, for example `dev` or `prd`
- `--pnr=<PNR>`: required PNR number
- `--output=<output-directory>`: required local output directory
- `--config=<properties-file>`: optional properties file path

## Azure Blob Structure

Azure Blob Storage uses virtual folder prefixes, not real directories. The application performs a flat prefix listing for:

```text
invision_sbr_audit/ABC123/
```

and downloads every matching blob beneath that prefix.

Example blob names:

```text
invision_sbr_audit/ABC123/0/1756383920000/request.json
invision_sbr_audit/ABC123/0/1756383920000/response.json
invision_sbr_audit/ABC123/1/1756385000000/nested/details.json
```

## Example Local Output

If the output directory is `/home/user/downloads`, the application recreates:

```text
/home/user/downloads/
└── invision_sbr_audit/
    └── ABC123/
        ├── 0/
        │   └── 1756383920000/
        │       ├── request.json
        │       └── response.json
        └── 1/
            └── 1756385000000/
                └── nested/
                    └── details.json
```

## Troubleshooting

- `Properties file not found`: check the default `./config/application-dev.properties` or `./config/application-prd.properties`, or pass `--config=<path>`.
- `Missing configuration property`: add the missing `storage-account` or `container` entry to the selected file.
- `403` or RBAC errors: confirm the signed-in identity has `Storage Blob Data Reader` or equivalent access.
- Azure login unavailable: run `az login` locally, or use Managed Identity in Azure.
- Container not found: verify the configured storage account and container values.
- Local permission denied: ensure the output directory exists or can be created and is writable.
