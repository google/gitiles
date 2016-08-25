# Configuration

The `gitiles.config` file supporting the site contains several configuration
options.

[TOC]

## Markdown

### Disabling markdown

Markdown can be completely disabled by setting render to false.

```
[markdown]
  render = false
```

### Markdown size

Markdown files are limited by default to 5 MiB of input text
per file. This limit is configurable, but should not be raised
beyond available memory.

```
[markdown]
  inputLimit = 5M
```

### Image size

Referenced [images are inlined](#Images) as base64 encoded URIs.
The image limit places an upper bound on the byte size of input.

```
[markdown]
  imageLimit = 256K
```

## Google Analytics

[Google Analytics](https://www.google.com/analytics/) can be
enabled on every rendered markdown page by adding the Property ID
to the configuration file:

```
[google]
  analyticsId = UA-XXXX-Y
```
