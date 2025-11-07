# MyBible Modules

This page explains what MyBible modules are, where to get them, and how to manage them.

## What are MyBible Modules?

MyBible modules are SQLite database files (`.sqlite3` extension) containing biblical text. They follow the MyBible format specification and include:

- Biblical text in various translations
- Book names and metadata
- Optional features:
    - Strong's numbers
    - Morphological data
    - Cross-references
    - Footnotes

## Where to Get Modules

### Primary Source: ph4.org

The main repository for MyBible modules is **[ph4.org](https://ph4.org/)**

Browse available Bibles, commentaries, and other resources.

### Other Sources

- MyBible official website (for mobile app modules)
- Community contributions
- Self-created modules (if you have the tools)

## Installing Modules

### Step 1: Download

1. Visit [ph4.org](https://ph4.org/)
2. Browse or search for desired translation
3. Download the `.zip` file

### Step 2: Extract

Unzip the downloaded file to get the `.sqlite3` file:

```bash
# Linux/macOS
unzip KJV.zip

# Windows - use built-in or 7-Zip
```

### Step 3: Organize

Create a dedicated folder for your modules:

```bash
# Recommended structure
MyBible/
└── modules/
    ├── KJV.sqlite3
    ├── NET.sqlite3
    ├── ESV.sqlite3
    └── ...
```

### Step 4: Configure

Tell mybible-cli where your modules are:

```bash
mybible-cli list --path "/path/to/MyBible/modules"
```

## Managing Modules

### List All Modules

```bash
mybible-cli list
```

Output:
```
Module path: /home/user/MyBible/modules

Available modules:
  ESV - English Standard Version
  KJV - King James Version
  NET - New English Translation
  NASB - New American Standard Bible

Total: 4 modules
```

### Check Module Details

```bash
mybible-cli get -m NET -r "John 3:16" --json
```

Inspect the JSON output to see:
- Module name
- Available features (Strong's, morphology, etc.)
- Text format

### Module Information

Use the `parse` command to test a module:

```bash
mybible-cli parse -r "John 3:16"
```

## Popular Modules

### English Translations

| Module | Description | Features |
|--------|-------------|----------|
| **KJV** | King James Version (1611) | Classic, poetic language |
| **ESV** | English Standard Version | Literal, modern |
| **NET** | New English Translation | Extensive notes, free |
| **NASB** | New American Standard | Very literal |
| **NIV** | New International Version | Thought-for-thought |

### Other Languages

MyBible format supports hundreds of translations in various languages:

- Spanish: RVR1960, BTX
- German: Luther1912, Schlachter
- Russian: RUSV, RST
- French: LSG, BDS
- Portuguese: ACF, ARA
- And many more...

## Module Features

### Strong's Numbers

Some modules include Strong's concordance numbers:

```bash
mybible-cli get -m KJV -r "John 1:1" -f "%X"
```

Output shows Strong's numbers like `<S>G3056</S>` (logos).

### Formatting Tags

Modules may include:

- `<pb />` - Paragraph breaks
- `<t>text</t>` - Title/heading
- `<J>text</J>` - Words of Jesus (red letter)
- `<i>text</i>` - Italics
- `<n>text</n>` - Footnote
- `<S>G1234</S>` - Strong's number

Use format specifiers to control how these are rendered:

- `%T` - Raw text with all tags
- `%t` - Clean multi-line text
- `%X` - Formatted with Strong's
- `%Y` - Formatted without Strong's
- `%Z` - Single-line formatted

See [Output Formatting](cli/formatting.md) for details.

## Module Compatibility

### Supported

- ✅ Bible text modules (`.SQLite3`)
- ✅ All MyBible-compatible translations
- ✅ Modules with Strong's numbers
- ✅ Modules with morphological data

### Not Currently Supported

- ❌ Commentary modules
- ❌ Dictionary modules
- ❌ Devotional modules
- ❌ Non-Bible content

!!! note
    This tool is designed specifically for Bible text retrieval. Other MyBible module types are not supported.

## Creating Custom Book Name Mappings

If you need custom abbreviations (e.g., for different languages):

Create a text file with mappings:

```text
# custom-books.txt
Genesis|Gen|Ge|Gn
Exodus|Exo|Ex
...
John|Jn|Jean|Juan
```

Use it:

```bash
mybible-cli get -r "Jn 3:16" -b custom-books.txt
```

See [Configuration](configuration.md) for details.

## Troubleshooting

### "No modules found"

```bash
# Check path is set
mybible-cli list

# Set path if needed
mybible-cli list --path "/correct/path/to/modules"
```

### "Module not found: XYZ"

```bash
# List available modules
mybible-cli list

# Check spelling and case
mybible-cli get -m ESV -r "John 3:16"  # Case-sensitive
```

### Corrupted Module

If a module appears corrupted:

1. Re-download from source
2. Verify it's unzipped correctly
3. Check file extension is `.sqlite3`

### Module Path Not Persisting

The path is stored in `config.json`. Location:

- **Windows**: `%APPDATA%\mybible-cli-java\config.json`
- **macOS**: `~/Library/Application Support/mybible-cli-java/config.json`
- **Linux**: `~/.config/mybible-cli-java/config.json`

## Next Steps

- [Quick Start](quickstart.md) - Start using modules
- [CLI Reference](cli/index.md) - Command-line usage
- [Output Formatting](cli/formatting.md) - Customize display
