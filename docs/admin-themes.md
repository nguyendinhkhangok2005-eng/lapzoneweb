# Admin themes

The shared [head fragment](../src/main/resources/templates/admin/theme.html) loads
[the synchronous bootstrap](../src/main/resources/static/js/admin-theme.js) and
[the scoped stylesheet](../src/main/resources/static/css/admin-themes.css).
Include it at the end of an Admin page's head, after Bootstrap and page styles.
AI subpages inherit it through `knowledgeHead`. Shared support templates include
it only for `adminView`. The sidebar fragment includes the reusable toolbar from
the same theme fragment. Printed reports include this toolbar on screen and hide
it when printing, keeping their print styles.

## Shared header selector

The compact selector appears at the top right. On pages with a `top-bar`, the
existing script moves the same control into that header; other pages use the
minimal shared toolbar. It offers four previews, each with four color swatches.
Native details/summary controls support keyboard toggling and tab navigation;
Escape and choosing a theme close the panel and return focus to its summary.

`window.LapzoneAdminTheme.themes` exposes the four IDs and Vietnamese labels:
`classic`, `dark`, `gaming`, `aurora`. Use `LapzoneAdminTheme.get()` to read the
active ID and `LapzoneAdminTheme.set(id)` to apply and persist a valid choice.
Invalid choices return `false` without changing anything. Successful selections
emit `lapzone:admin-theme-change` with `event.detail.theme`.

Preferences use only `lapzone-admin-theme` in local storage. Missing, unsupported,
or inaccessible preferences fall back to Classic. A legacy `ocean` preference is
migrated to `aurora`, including when storage cannot be rewritten. Storage
events synchronize both the theme and selector across Admin tabs. The customer `data-theme`
attribute, `lapzone-theme` key, and customer theme scripts are independent.

## Styling

All tokens start with `--admin-` under `html[data-admin-theme]`. Existing page
styles continue to control Classic, including each page's original background.
The three alternative themes opt in explicitly; unknown attributes do not
activate their overrides. With JavaScript disabled, the original styles remain.

Tokens cover surfaces, headers, sidebar/navigation, cards, buttons, tables,
inputs, borders, focus, and semantic statuses. Bootstrap components and the
standalone Admin components consume those tokens. Chart.js labels and grid lines
are adapted by a presentation plugin; chart data and interactions are unchanged.

Aurora uses solid cyan primary buttons with dark text for contrast. Its bright
violet/pink/orange gradient is reserved for decorative edges, active navigation,
and chart bars; the welcome highlight uses a darker version behind white text.
No animation, blur, extra fonts, or library is added by the theme system.

Run the dependency-free infrastructure checks with:

```sh
node --test src/test/js/admin-theme.test.cjs
```
