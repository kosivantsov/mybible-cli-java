# Quick Start Guide

Get up and running with MyBible CLI Java in minutes.

## Prerequisites

1. **Install the application** - See [Installation Guide](installation.md)
2. **Download MyBible modules** - See [MyBible Modules](modules.md)

## First-Time Setup

### Step 1: Set Module Path

Tell the application where your MyBible modules are stored:

```bash
mybible-cli list --path "/path/to/your/MyBible/modules"
```

**Example paths:**

=== "Windows"
    ```cmd
    mybible-cli list --path "C:\Users\YourName\Documents\MyBible\modules"
    ```

=== "macOS"
    ```bash
    mybible-cli list --path "$HOME/Documents/MyBible/modules"
    ```

=== "Linux"
    ```bash
    mybible-cli list --path "$HOME/MyBible/modules"
    ```

### Step 2: List Available Modules

```bash
mybible-cli list
```

You should see your installed modules:

```
Available modules:
  - KJV (King James Version)
  - NET (New English Translation)
  - ESV (English Standard Version)
```

## Basic Usage

### CLI Mode

#### Fetch a Single Verse

```bash
mybible-cli get -m KJV -r "John 3:16"
```

Output:
```
For God so loved the world, that he gave his only begotten Son...
```

#### Fetch Multiple Verses

```bash
mybible-cli get -m ESV -r "Romans 8:28-30"
```

#### Fetch Multiple Passages

```bash
mybible-cli get -m NET -r "John 3:16; Romans 8:28; Ephesians 2:8-10"
```

#### Use Last Module

```bash
# After first use, you can omit -m
mybible-cli get -r "Psalm 23"
```

### GUI Mode

Launch the GUI:

```bash
mybible-cli gui
```

Or with a pre-loaded passage:

```bash
mybible-cli gui -m KJV -r "John 3:16"
```

**In the GUI:**

1. Select module from dropdown
2. Enter reference (e.g., "John 3:16")
3. Press ++enter++ or click "Get Verses"
4. Use ++ctrl+l++ / ++cmd+l++ to focus reference field
5. Use ++ctrl+m++ / ++cmd+m++ to focus module dropdown

## Common Tasks

### Read a Chapter

```bash
mybible-cli get -m ESV -r "Psalm 23"
```

### Read Multiple Chapters

```bash
mybible-cli get -m KJV -r "Matthew 5-7"
```

### Custom Output Format

```bash
# Show verse numbers before text
mybible-cli get -m NET -r "Romans 8:28-30" -f "%v. %t"
```

Output:
```
28. And we know that...
29. For those whom he foreknew...
30. And those whom he predestined...
```

### Copy Output to Clipboard

=== "Linux"
    ```bash
    mybible-cli get -m KJV -r "John 3:16" | xclip -selection clipboard
    ```

=== "macOS"
    ```bash
    mybible-cli get -m ESV -r "Psalm 23" | pbcopy
    ```

=== "Windows"
    ```cmd
    mybible-cli get -m NET -r "Romans 8:28" | clip
    ```

### Save to File

```bash
mybible-cli get -m KJV -r "Psalm 119" > psalm119.txt
```

### Get JSON Output

```bash
mybible-cli get -m NET -r "John 3:16" --json
```

```json
{
  "module": "NET",
  "verses": [
    {
      "book": 500,
      "chapter": 3,
      "verse": 16,
      "text": "For this is the way God loved the world..."
    }
  ]
}
```

## Keyboard Shortcuts (GUI)

| Shortcut | Action |
|----------|--------|
| ++esc++ | Quit application |
| ++ctrl+l++ / ++cmd+l++ | Focus reference input |
| ++ctrl+m++ / ++cmd+m++ | Focus module dropdown |
| ++ctrl+j++ / ++cmd+j++ | Toggle JSON view |
| ++ctrl+shift+c++ / ++cmd+shift+c++ | Copy formatted text |
| ++cmd+comma++ (macOS) | Open settings |

## Tips & Tricks

### Create Shell Alias

Add to your `~/.bashrc` or `~/.zshrc`:

```bash
alias bible='mybible-cli get'
```

Now you can use:

```bash
bible -r "John 3:16"
```

### Daily Verse Script

```bash
#!/bin/bash
# daily-verse.sh
DAY=$(date +%d)
mybible-cli get -m ESV -r "Proverbs $DAY"
```

### Search Within Output

```bash
mybible-cli get -m KJV -r "John 1" | grep -i "word"
```

### Count Words in Passage

```bash
mybible-cli get -m ESV -r "Genesis 1" -f "%z" | wc -w
```

## Next Steps

Now that you're familiar with the basics:

- [CLI Reference](cli/index.md) - Complete CLI documentation
- [GUI Guide](gui/index.md) - Detailed GUI usage
- [Output Formatting](cli/formatting.md) - Custom format strings
- [Examples & Recipes](examples.md) - Advanced use cases
- [Configuration](configuration.md) - Customize behavior

## Getting Help

```bash
# General help
mybible-cli help

# Command-specific help
mybible-cli help get
mybible-cli help list
mybible-cli help parse

# List all help topics
mybible-cli help --topics
```
