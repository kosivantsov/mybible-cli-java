### JAR File
:bulb: <span style="color: grey;">Use this option if you have Java installed on your system and know how to run applications using JAR files.</span>
??? info "Java Runtime Requirements"
    To run `mybible-cli.jar`, you will need JRE or JDK 11 or higher.

    Check your Java version:
    ```
    java -version
    ```

1. Download `mybible-cli.jar` from [Releases](https://github.com/kosivantsov/mybible-cli-java/releases).
2. Double-click the `mybible-cli.jar` if your OS supports running Java apps this way.  
   This will open the GUI where you can type the reference and select the module.  
   You may also start the GUI with the reference and the module preselected:
    ```
    # Enter the directory with mybible-cli.jar
    cd Downloads

    # Start the GUI with the reference and the module preselected
    java -jar mybible-cli.jar gui -m KJV -r "John 3:16"
    ```
3. To get the output in the terminal window, use the required [CLI options](cli/index.md):
    ```
    # Enter the directory with mybible-cli.jar
    cd Downloads

    # Get the required text by specifying the reference and the module
    java -jar mybible-cli.jar get -m KJV -r "John 3:16"
    ```
