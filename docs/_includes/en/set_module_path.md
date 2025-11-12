=== "CLI"

    ```
    mybible-cli list --path "/path/to/your/MyBible/modules"
    ```

    **Example paths:**

    === "Windows"
        ```
        mybible-cli list --path "C:\Users\YourName\Documents\MyBible\modules"
        ```

    === "macOS"
        ```
        mybible-cli list --path "$HOME/Documents/MyBible/modules"
        ```

    === "Linux"
        ```
        mybible-cli list --path "$HOME/MyBible/modules"
        ```

    #### List Available Modules

    ```
    mybible-cli list
    ```

    You should see your modules sorted by language:

    ![List modules in CLI mode](../assets/images/macCLI_list.png){: width="60%"}

=== "GUI"

    1. Launch the GUI by opening the application from the Start menu, double-clicking the JAR file, or from the terminal:
        ```
        mybible-cli
        ```
    2. Click "Set Module Path".

        ![Set Module Path in GUI](../assets/images/macGUI_setModulePath.png){: width="60%"}
    3. If you selected the correct folder, you will be able to see the list of modules sorted by language.

        ![Module List in GUI](../assets/images/macGUI_list.png){: width="60%"}

Setting the module path needs to be done only once. The path will be saved for future use.

??? tip "Switch module folder"
    If you need to switch to a different module folder, simply repeat the above and specify the folder you want to use.