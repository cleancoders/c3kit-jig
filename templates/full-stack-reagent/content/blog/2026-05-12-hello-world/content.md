# Hello, world

This is the example blog post that ships with the starter template.

It demonstrates the content pipeline:

- Markdown source in `content/<type>/<permalink>/content.md`.
- Metadata in `meta.edn`.
- Auto-registered routes at `/<type>` and `/<type>/:permalink`.
- Server-rendered HTML preview for SEO.
- `Accept: text/markdown` returns the raw source for AI agents.

You can also drop a hiccup-shaped slot on its own line to render a
reagent component registered under that keyword — for example:

[:quote-block {:text "Programs must be written for people to read, and only incidentally for machines to execute." :attribution "Harold Abelson"}]

The `quote-block` component is wired in `src/cljs/acme/content/page.cljs`.

Delete this directory once you've got your own content.
