??? tip "Fix Garbled Terminal Output on Windows"
    If your output is garbled...

    - ...and you use **`mybible-cli-console.exe`**

        Use one of the helper scripts instead of `mybible-cli-console.exe`:

        * `mybible-cli.ps1`
        * `mybible-cli-unicode.bat`
        * `mybible-cli-utf8.bat`

        All of them accept the same options and arguments as `mybible-cli-console.exe`.

    - ...and you use **`mybible-cli.jar`**

        Start Java with additional runtime options:

        ```
        java "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8" -jar mybible-cli.jar get -r "Jn 3:16"
        ```

??? tip "Create Shell Alias on macOS and Linux"
    - Make it possible to run `mybible-cli` without giving the full path.

        Add to your `~/.bashrc` or `~/.zshrc`:
        ```bash
        # If using JAR
        alias mybible-cli='java -jar /path/to/mybible-cli.jar'
        
        # Otherwise
        alias mybible-cli='/path/to/mybible-cli'
        ```
    - Fetch verses even faster:
        ```bash
        alias bible='mybible-cli get'
        ```

    - Now you can use:
        ```bash
        bible -r "John 3:16"
        ```

??? tip "Daily Provers Script"
    ```bash
    #!/bin/bash
    # daily-verse.sh
    DAY=$(date +%d)
    mybible-cli get -m ESV -r "Proverbs $DAY"
    ```

??? tip "Search in Output"
    ```bash
    mybible-cli get -m KJV+ -r "John 1" | grep -i "word"
    ```

??? tip "Count Words in Passage"
    ```bash
    mybible-cli get -m ESV -r "Genesis 1" -f "%z" | wc -w
    ```