# Installation

This page provides detailed installation instructions for MyBible CLI Java on different platforms.

## Requirements

### For Pre-built Applications

Pre-built applications include the required Java runtime and have no external dependencies.

### For Running JAR Files

- **Java Runtime**: JDK or JRE 17 or higher
- **MyBible Modules**: At least one `.sqlite3` module file

Check your Java version:

```bash
java -version
```

## Installation Methods

=== "Windows"

    ### Pre-built Application

    1. Download the installer from [Releases](https://github.com/kosivantsov/mybible-cli-java/releases):
        - `mybible-cli-windows-gui.msi` - GUI only, no terminal window
        - `mybible-cli-windows-cli-gui.msi` - Both CLI and GUI (terminal opens with GUI)
    
    2. Run the installer
    3. Choose installation location
    4. Application will be added to Start Menu

    ### Using the CLI (cli-gui version)

    ```cmd
    # From Command Prompt
    mybible-cli get -r "John 3:16"
    ```

    ### JAR File

    1. Download `mybible-cli.jar` from Releases
    2. Run: `java -jar mybible-cli.jar`

=== "macOS"

    ### Pre-built Application

    1. Download `mybible-cli-macos.dmg` from [Releases](https://github.com/kosivantsov/mybible-cli-java/releases)
    2. Open the DMG file
    3. Drag application to Applications folder
    4. Right-click and select "Open" first time (security requirement)

    ### Command Line Usage

    ```bash
    # Add to PATH (optional)
    sudo ln -s /Applications/MyBible-CLI.app/Contents/MacOS/MyBible-CLI /usr/local/bin/mybible-cli

    # Now you can run from terminal
    mybible-cli get -r "John 3:16"
    ```

    ### JAR File

    ```bash
    # Download
    curl -LO https://github.com/kosivantsov/mybible-cli-java/releases/latest/download/mybible-cli.jar

    # Run
    java -jar mybible-cli.jar
    ```

=== "Linux"

    ### Debian/Ubuntu (.deb)

    ```bash
    # Download .deb file
    wget https://github.com/kosivantsov/mybible-cli-java/releases/latest/download/mybible-cli_1.0_amd64.deb

    # Install
    sudo dpkg -i mybible-cli_1.0_amd64.deb

    # Run CLI
    mybible-cli get -r "John 3:16"

    # Run GUI
    mybible-cli gui
    ```

    ### RPM-based (Fedora, RHEL)

    ```bash
    # Download .rpm file
    wget https://github.com/kosivantsov/mybible-cli-java/releases/latest/download/mybible-cli-1.0.x86_64.rpm

    # Install
    sudo rpm -i mybible-cli-1.0.x86_64.rpm
    ```

    ### JAR File

    ```bash
    # Download
    wget https://github.com/kosivantsov/mybible-cli-java/releases/latest/download/mybible-cli.jar

    # Run
    java -jar mybible-cli.jar
    ```

## Getting MyBible Modules

After installation, you need to download MyBible modules:

1. **Download modules** from [ph4.org](https://ph4.org/) or other sources
2. **Unzip** the downloaded files (`.zip` → `.sqlite3`)
3. **Store** in a dedicated folder (e.g., `~/MyBible/modules/`)
4. **Set path** using:
   ```bash
   mybible-cli list --path "/path/to/your/modules"
   ```

See [MyBible Modules](modules.md) for detailed information about available modules and sources.

## Verification

Test your installation:

=== "CLI"

    ```bash
    # Check help
    mybible-cli help

    # Set module path (first time)
    mybible-cli list --path "/path/to/modules"

    # List available modules
    mybible-cli list

    # Fetch a verse
    mybible-cli get -m KJV -r "John 3:16"
    ```

=== "GUI"

    ```bash
    # Launch GUI
    mybible-cli gui
    ```

    1. Click "Set Module Path" if prompted
    2. Select your modules folder
    3. Choose a module from dropdown
    4. Enter a reference (e.g., "John 3:16")
    5. Click "Get Verses"

## Troubleshooting

### "Command not found" (Linux/macOS)

```bash
# Check if installed
which mybible-cli

# If using JAR, create alias
alias mybible-cli='java -jar /path/to/mybible-cli.jar'

# Make permanent (add to ~/.bashrc or ~/.zshrc)
echo "alias mybible-cli='java -jar /path/to/mybible-cli.jar'" >> ~/.bashrc
```

### Java Version Issues

```bash
# Check Java version
java -version

# Should be 17 or higher
# If not, install newer Java:

# Ubuntu/Debian
sudo apt install openjdk-17-jre

# macOS (using Homebrew)
brew install openjdk@17

# Fedora
sudo dnf install java-17-openjdk
```

### macOS Security Warning

If you see "cannot be opened because it is from an unidentified developer":

1. Right-click the application
2. Select "Open"
3. Click "Open" in the security dialog

## Next Steps

- [Quick Start Guide](quickstart.md) - Get up and running in minutes
- [CLI Documentation](cli/index.md) - Learn command-line usage
- [GUI Documentation](gui/index.md) - Explore the graphical interface
