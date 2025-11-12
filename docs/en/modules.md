# MyBible Modules

This page explains what MyBible modules are, where to get them, and how to manage them with mybible-cli.

## What are MyBible Modules?

MyBible modules are SQLite database files (`.sqlite3` extension) containing biblical text and related content. They follow the MyBible format specification and can include:

- Biblical text in various translations
- Book names and metadata
- Optional features such as Strong's numbers, morphological data, cross-references, and footnotes

## Module Types

MyBible format specification supports several types of modules. More information about module types can be found at [mybible.zone/modules](https://mybible.zone/modules/).

!!! note
    `mybible-cli` is designed specifically for Bible text retrieval, not Bible study. Only Bible modules are supported.
    
### Supported by `mybible-cli`

- ✅ **Bible text modules** - Translations of the Bible (complete or partial)

### Not Supported

- ❌ Commentary modules
- ❌ Dictionary modules
- ❌ Devotional modules
- ❌ Other non-Bible content types

## Where to Get Modules

### Web Download: ph4.org

A convenient place to browse and download MyBible modules is **[ph4.org Bible section](https://www.ph4.org/b4_index.php?hd=b)**.

- Browsable collection of multiple Bible translations
- Direct downloads
- Easy access to modules in various languages

??? info "Mobile App Sources"
    The MyBible app for [Android](https://play.google.com/store/apps/details?id=ua.mybible) and [iOS](https://apps.apple.com/ru/app/mybible/id1166291877) downloads modules from several dedicated repositories, but they don't have a web interface for browsing.

### Command-Line Module Manager: mybible-get

If you're comfortable with command-line tools and have Python installed, you can use **[`mybible-get`](https://github.com/kosivantsov/mybible-get)** – a command-line module manager similar to `apt`, `brew`, or `choco`.

!!! warning "Important: Use Separate Folders"
    Modules managed by `mybible-get` and modules downloaded/unpacked manually should be kept in **separate folders** to avoid conflicts.

**`mybible-get`** can:

- Search for modules
- Download modules directly from repositories used by the MyBible mobile app
- Update existing modules
- Maintain mulpiple module collections

## Installing Modules

=== "Manual Download and Extract"
    
    #### Step 1: Download
    
    1. Visit [ph4.org Bible section](https://www.ph4.org/b4_index.php?hd=b)
    2. Browse or search for your desired translation
    3. Download the `.zip` file
    
    #### Step 2: Extract
    
    Unzip the downloaded file to get the `.sqlite3` file
    
    
    #### Step 3: Organize
    
    Create a dedicated folder for your modules
    
    ```
    # Recommended structure
    MyBible/
    └── modules/
        ├── KJV.sqlite3
        ├── NET.sqlite3
        ├── ESV.sqlite3
        └── ...
    ```

=== "Using `mybible-get`"

    !!! note
        See the [`mybible-get` documentation](https://github.com/kosivantsov/mybible-get) for complete usage instructions.

    If you have Python installed:

    ```
    # Install mybible-get
    pipx install mybible-get
    ```

    #### Step 1: Tell `mybible-get` where your modules need to be located:

    ```
    # Set a separate folder for mybible-get modules (will be created if needed)
    mybible-get set-path ~/MyBible-modules
    ```

    #### Step 2: Install modules

    ```
    # Search for modules using the module name
    mybible-get search -n KJV

    # Install a module using one or more of the search results
    mybible-get install KJV1611, KJV+
    ```

## Set Module Path

Once you have at least one Bible module, you need to tell `mybible-cli` where to look for modules.

{!en/set_module_path.md!}

{!en/modules_basics.md!}

### Default Module

The last used module is remembered for both CLI and GUI. After using a module once, you can omit `-m`:

```
# First use
mybible-cli get -m KJV+ -r "Psalm 23"

# Subsequent uses (KJV+ is now default)
mybible-cli get -r "John 1:1"
```

### Module Features

Some modules include enhanced features:

- **Strong's Numbers** - Original Hebrew/Greek word references
- **Morphological Data** - Grammatical information
- **Footnotes** - Translator notes and variants
- **Formatting** - Bold, italics, etc.
- **Words of Jesus**

To control how these features are displayed, use format specifiers. See [Output Formatting](formatting.md) for details.

Footnotes are always omitted in the output.

??? tip "Check Module Features"
    The raw verse text (`rawVerseText` in JSON or `%T` format specifier) may be used to check what features a module includes:
    ```
    # Regular output
    mybible-cli get -m KJV -r "John 1:1" -f "%T"
    
    # JSON output
    mybible-cli get -m KJV -r "John 1:1" --json
    ```
    
    Look for tags like:
        
    - `<S>G3056</S>` - Strong's numbers
    - `<f>footnote reference</f>` - Footnotes
    - `<J>text</J>` - Words of Jesus

### Module Book Names

Each module defines its own book names (both full and abbreviated). You can:

- Use default international book abbreviations
- Use module-specific abbreviations with `-A` flag in CLI, or by checking the "Use Bible Book Names from the Selected Module" checkbox in GUI
- Use custom mapping files with `-a` flag in CLI, or by selecting the file for "Bible Book Names" in GUI
- Specify a language for book name recognition with `-l` flag in CLI, or by specifying the language code in the "Names Lookup Language" field in GUI

See [Book Name Mapping](mapping.md) for complete details on customizing book name recognition.

## Next Steps

- [CLI Documentation](cli/index.md) - Learn command-line usage
- [GUI Documentation](gui/index.md) - Explore the graphical interface
- [Output Formatting](formatting.md) - Custom format strings
- [Examples & Recipes](examples.md) - Advanced use cases