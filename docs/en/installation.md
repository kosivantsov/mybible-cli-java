# Installation

Learn how to install `mybible-cli` on different platforms.

{!en/need_modules.md!}

## Installation Methods
- [Pre-built Application](#pre-built-application)
- [JAR File](#jar-file)
- [Building from Source](#building-from-source)

### Pre-built Application

:bulb: <span style="color: grey;">Use this option if you don't have Java installed on your system.</span>

=== "Windows"

    On Windows, you may use one of two versions (or both):

    #### CLI and GUI Version
    <details open><summary>Installation Steps</summary>
    This version can output biblical text in both modes (GUI always opens with or from a terminal window).

    1. Download `mybible-cli-Windows-Console.zip` from [Releases](https://github.com/kosivantsov/mybible-cli-java/releases).  
       This is a zipped portable application.
    2. Unpack the portable version.
    3. Place the unpacked portable application in a convenient location.
    4. Run the application by double-clicking `mybible-cli-console.exe` to launch it in GUI mode, or run in the terminal:
        ```
        :: Enter the directory with mybible-cli
        cd C:\Programs\mybible-cli-console

        :: Get the biblical text
        mybible-cli-console.exe get -m KJV -r "John 3:16"
        ```
    </details>

    #### GUI-Only Version
    <details open><summary>Installation Steps</summary>
    This version outputs biblical text only in GUI mode.
    
    1. Download `mybible-cli-Windows-GUI.zip` from [Releases](https://github.com/kosivantsov/mybible-cli-java/releases).  
       This is a zipped MSI installer.
    2. Unpack and run the installer.
    3. Select the installation destination, or accept the suggested one.
    4. Run the application by clicking the `mybible-cli` icon in the Start menu.  
       This will open the GUI where you can type the reference and select the module.  
       You may also start the GUI with the reference and the module preselected:
        ```
        :: Enter the directory with mybible-cli
        cd C:\Program Files\mybible-cli

        :: Start the GUI with the reference and the module preselected
        mybible-cli.exe gui -m KJV -r "John 3:16"
        ```

        !!! warning "The GUI-only version of the application cannot be used to retrieve biblical text in command-line mode. Use the portable [CLI and GUI Version](#cli-and-gui-version)."
    </details>

=== "macOS"

    1. Download `mybible-cli-macOS.zip` from [Releases](https://github.com/kosivantsov/mybible-cli-java/releases).
    2. Unzip the downloaded file.
    3. Drag the unzipped application to the Applications folder.
    4. Right-click and select "Open" the first time (security requirement).

        ??? tip "macOS Security Warning"
            If your macOS complains that the application cannot be opened because it is from an unidentified developer:
    
            1. Right-click the application.
            2. Select "Open".
            3. Open "System Settings" from the menu.
            4. Navigate to the "Privacy & Security" section.
            5. Select "mybible-cli.app" and click "Open anyway".

    5. The application can be used in both CLI and GUI modes.

        To start the GUI, simply open the application.

        To use the CLI, run with the required [CLI options](cli/index.md):
        ```
        /Applications/mybible-cli.app/Contents/MacOS/mybible-cli get -m KJV -r "Jn 3:16"
        ```

=== "Linux"

    !!! info "Pre-built binaries are available only for Debian-based distros."

    1. Download `mybible-cli-Linux-DEB.zip` from [Releases](https://github.com/kosivantsov/mybible-cli-java/releases).
    2. Unzip the downloaded file.
    3. Install the unzipped `.deb` file:
        ```
        sudo dpkg -i mybible-cli_<version>_amd64.deb ## Replace <version> with the actual version number
        ```
    4. The application can be used in both CLI and GUI modes.

        To start the GUI, simply open the application from the application menu in your DE.

        To use the CLI, run with the required [CLI options](cli/index.md):
        ```
        /opt/mybible-cli/bin/mybible-cli get -m KJV -r "Jn 3:16"
        ```

{!en/jar_installation.md!}

### Building from Source
:bulb: <span style="color: grey;">If you consider this option, you probably know why and when to use it.</span>
??? info "Java Development Kit Requirements"
    To build the application, you will need JDK version 17 or higher.

1. Clone the `mybible-cli-java` repository:

    ```
    git clone https://github.com/kosivantsov/mybible-cli-java.git
    ```

2. Build the JAR:

    ```
    ./gradlew clean shadowJar
    ```

3. Run the application:

    ```
    # Launch the GUI
    java -jar build/jpackage/mybible-cli.jar
    
    # Launch the GUI with the preselected reference and module:
    java -jar build/jpackage/mybible-cli.jar gui -m KJV -r "Jn 3:16"
    
    # Or use in the terminal
    java -jar build/jpackage/mybible-cli.jar get -m KJV -r "Jn 3:16"
    ```


## Next Steps

- [Quick Start Guide](quickstart.md) - Get up and running in minutes
- [CLI Documentation](cli/index.md) - Learn command-line usage
- [GUI Documentation](gui/index.md) - Explore the graphical interface