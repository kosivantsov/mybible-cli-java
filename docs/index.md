# MyBible CLI Java

Welcome to the documentation for **MyBible CLI Java** — a fast, cross-platform command-line and GUI tool for accessing biblical text from MyBible modules.

<div class="grid cards" markdown>

-   :fontawesome-solid-terminal:{ .lg .middle } __Command-Line Interface__

    ---

    Fast verse fetching, flexible output formatting, and easy integration with other tools.

    [:octicons-arrow-right-24: CLI Documentation](cli/index.md)

-   :fontawesome-solid-window-maximize:{ .lg .middle } __Graphical Interface__

    ---

    Simple, themeable GUI with customizable text display and keyboard shortcuts.

    [:octicons-arrow-right-24: GUI Documentation](gui/index.md)

-   :fontawesome-solid-download:{ .lg .middle } __Get Started__

    ---

    Download pre-built applications or build from source.

    [:octicons-arrow-right-24: Installation Guide](installation.md)

-   :fontawesome-solid-book:{ .lg .middle } __Examples__

    ---

    Practical examples and recipes for common use cases.

    [:octicons-arrow-right-24: View Examples](examples.md)

</div>

## About This Project

MyBible CLI Java is inspired by `diatheke`, the standard command-line tool for Crosswire Sword modules. While `diatheke` is powerful, it works only with Crosswire Sword modules and does not support the MyBible format, which has an extensive library of high-quality modules.

### Key Features

- **Fast Verse Retrieval**: Quickly fetch and display verses from any MyBible module
- **Flexible Formatting**: Control output with customizable format strings
- **Book Name Mappings**: Use default abbreviations or create custom mappings
- **Dual Interface**: Use via command-line for scripting or GUI for interactive lookups
- **Cross-Platform**: Works on Windows, macOS, and Linux

### Purpose

This tool is designed as a **quick lookup tool** for biblical text, not a comprehensive Bible study application. It excels at:

- Fast text retrieval for use in other applications
- Scriptable access to biblical text
- Integration into automated workflows
- Quick reference lookups

## Quick Start

=== "Download Pre-built"

    Download the latest version from the [Releases page](https://github.com/kosivantsov/mybible-cli-java/releases).

    Platform-specific applications include Java runtime — no separate Java installation required.

=== "Build from Source"

    ```bash
    # Clone repository
    git clone https://github.com/kosivantsov/mybible-cli-java.git
    cd mybible-cli-java

    # Build JAR
    ./gradlew clean shadowJar

    # Run
    java -jar build/libs/mybible-cli.jar
    ```

!!! tip "You'll Need MyBible Modules"
    To use this application, you need at least one MyBible module (`.sqlite3` file). See [MyBible Modules](modules.md) for download sources.

## Next Steps

<div class="grid cards" markdown>

-   [:octicons-download-24: Installation](installation.md)
-   [:octicons-rocket-24: Quick Start](quickstart.md)
-   [:octicons-terminal-24: CLI Guide](cli/index.md)
-   [:octicons-browser-24: GUI Guide](gui/index.md)

</div>
