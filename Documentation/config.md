# Configuration

The `gitiles.config` file supporting the site contains several configuration
options.

[TOC]

## Core configuration

### Cross-Origin Resource Sharing (CORS)

Gitiles sets the `Access-Control-Allow-Origin` header to the
HTTP origin of the client if the client's domain matches a regular
expression defined in `allowOriginRegex`.

```
[gitiles]
  allowOriginRegex = http://localhost
```

By default `allowOriginRegex` is unset, denying all cross-origin requests.

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

### IFrames

IFrame source URLs can be whitelisted by providing a list of allowed
URLs. URLs ending with a `/` are treated as prefixes, allowing any source
URL beginning with that prefix.

```
[markdown]
  allowiframe = https://google.com/
```

URLs not ending with a `/` are treated as exact matches, and only those
source URLs will be allowed.


```
[markdown]
  allowiframe = https://example.com
  allowiframe = https://example.org
```

If the list has a single entry with the value `true`, all source URLs
will be allowed.


```
[markdown]
  allowiframe = true
```

## Google Analytics

[Google Analytics](https://www.google.com/analytics/) can be
enabled on every rendered markdown page by adding the Property ID
to the configuration file:

```
[google]
  analyticsId = UA-XXXX-Y
```
