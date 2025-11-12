# Quick Start Guide

Get up and running with `mybible-cli` in minutes.

## Prerequisites

To start using `mybible-cli`, you need to make sure that:

1. **It's present on your computer** – see [Installation Guide](installation.md).
2. You have at least one **unpacked MyBible module** – see [MyBible Modules](modules.md).

## First-time Setup

The only absolutely necessary thing that needs to be done prior to the first use is **telling the application where your MyBible modules are stored**.

If you have one folder with modules, this needs to be done only once.

If you have multiple folders with modules and want to switch between them, setting the module path needs to be done once before each switch.

The application uses the same configuration file regardless of the mode it runs in (CLI or GUI) or how it was launched (from terminal, in a script, as a JAR file, as a compiled OS-specific application, etc.).

{!en/cli_name.md!}

### Set Module Path

{!en/set_module_path.md!}

{!en/modules_basics.md!}

### Read a Chapter

=== "CLI"

    ```
    mybible-cli get -m ESV -r "Psalm 23"
    ```

=== "GUI"

    1. Press ++ctrl+l++ / ++cmd+l++ or click in the "Bible Reference" field.
    2. Enter the reference, and change the module if needed.
    3. Press ++enter++ or click "Show".

### Read Multiple Chapters

=== "CLI"

    ```
    mybible-cli get -m KJV -r "Matthew 5-7"
    ```

=== "GUI"

    1. Press ++ctrl+l++ / ++cmd+l++ or click in the "Bible Reference" field
    2. Enter the reference, and change the module if needed
    3. Press ++enter++ or click "Show".

### Copy Output to Clipboard

=== "CLI"

    === "Windows"
        ```
        mybible-cli get -m NET -r "Romans 8:28" | clip
        ```

    === "macOS"
        ```
        mybible-cli get -m NET -r "Romans 8:28" | pbcopy
        ```

    === "Linux"
        ```
        mybible-cli get -m NET -r "Romans 8:28" | xclip -selection clipboard
        ```

=== "GUI"

    1. Type the required reference and select the module.
    2. Press ++enter++ or click "Show".
    3. Press ++ctrl+shift+c++ / ++cmd+shift+c++ or click "Copy Text".

        ![Copy Text in GUI](../assets/images/macGUI_copy.png){: width="400"}

### Save to File

=== "CLI"
    ```
    mybible-cli get -m KJV -r "Psalm 119" > psalm119.txt
    ```

=== "GUI"

    1. Type the required reference and select the module.
    2. Press ++enter++ or click "Show".
    3. Press ++ctrl+shift+c++ / ++cmd+shift+c++ or click "Copy Text".
    4. Launch a text editor or a word processor, and create a new document.
    5. Paste the copied content. If the application supports extended formatting, some formatting will be kept.
    6. Save the newly created document.
    

### Get JSON Output

=== "CLI"

    ```
    mybible-cli get -m NET -r "John 3:16" --json
    ```

    **Output**:
    ```json
    [
      {
        "bookNumber": 500,
        "defaultFullBookName": "John",
        "defaultShortBookName": "John",
        "moduleFullBookName": "John",
        "moduleShortBookName": "Jn",
        "allBookNames": ["John", "Jhn", "Jn"],
        "chapter": 3,
        "verse": 16,
        "rawVerseText": "<pb/> <J>For this is the way <f>[36]</f> God loved the world: He gave his one and only <f>[37]</f> Son, so that everyone who believes in him will not perish <f>[38]</f> but have eternal life.</J> <f>[39]</f>",
        "moduleName": "NET"
      }
    ]
    ```

=== "GUI"

    1. Type the required reference and select the module.
    2. Press ++enter++ or click "Show".
    3. Press ++ctrl+j++ / ++cmd+j++ or mark the "Show Raw JSON" checkbox.

        ![JSON in GUI](../assets/images/macGUI_JSON.png){: width="400"}

## Next Steps

Now that you're familiar with the basics:

- [CLI Documentation](cli/index.md) - Learn command-line usage
- [GUI Documentation](gui/index.md) - Explore the graphical interface
- [Output Formatting](formatting.md) - Custom format strings
- [Examples & Recipes](examples.md) - Advanced use cases