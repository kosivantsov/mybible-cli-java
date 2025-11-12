## Module Usage

=== "CLI"

    ??? tip "Using Quotation Marks"
        If the module name you want to use contains special characters or spaces, always specify it in quotation marks.

        The same is true for references, unless you want to fetch verses from an entire book.

        Examples:
        ```
        # No special characters/spaces in the module name or reference
        mybible-cli get -m KJV+ -r Lk
        
        # With special character and spaces
        mybible-cli get -m "НПУ'22" -r "Luke 2:2"
        ```

    ### Fetch a Single Verse

    ```
    mybible-cli get -m KJV+ -r "John 3:16"
    ```

    Output:
    ```
    John 3:16 For God so loved the world, that he gave his only begotten Son, that whosoever believeth in him should not perish, but have everlasting life.
    ```

    ### Fetch Multiple Verses

    ```
    mybible-cli get -m ESV -r "Romans 8:28-30"
    ```

    ### Fetch Multiple Passages

    ```
    mybible-cli get -m NET -r "John 3:16; Romans 8:28; Ephesians 2:8-10"
    ```

=== "GUI"

    ### Fetch Verses

    Launch the GUI by opening the application from the Start menu, double-clicking the JAR file, or from the terminal:

    ```
    # Start with an empty output
    mybible-cli

    # Or with a pre-loaded passage:
    mybible-cli gui -m KJV+ -r "John 3:16"
    ```

    **In the GUI:**

    1. Select a module from the dropdown.
    2. Enter a reference, for example:

        * `John 3:16`
        * `Romans 8:28-30`
        * `John 3:16; Ephesians 2:8-10; 1 Peter-2 Peter 2:1`

        This can be one verse, several verses, several chapters, or several passages, including entire books.

    3. Press ++enter++ or click "Show".

        ![Show Passages in GUI](../assets/images/macGUI_showPassages.png){: width="60%"}