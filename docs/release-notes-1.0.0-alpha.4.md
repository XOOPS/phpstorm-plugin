# XOOPS Support 1.0.0 Alpha 4

A rebuild of the plugin on the PHP PSI, for PhpStorm 2024.3 and later.

- **52 inspections**, up from 10: bootstrap guards, database calls, security, Smarty templates, language files,
  installer SQL, deprecated core API. Every quick-fix is backed by a whole-file fixture and applied twice, so
  running it again changes nothing.
- **Tuned on real code.** The set was run over 224 modules and six times over XoopsCore27, from 2,241 findings
  down to 666, with no findings in test trees and every remaining one checked against the source.
- **Core-version aware.** `isResultSet()` and `exec()` rewrites are offered only when the tree's
  `include/version.php` has them; older cores get `if (!$result)` and `queryF()`. The version can be overridden in
  Settings → XOOPS Support.
- **Carried over from Alpha 3 and rebuilt:** the Overview tool window, Settings page, New → XOOPS Module… scaffold,
  Tools → XOOPS Support menu, and Ctrl+B / Find Usages on language constants in PHP and templates.
- Installs and updates without a restart.

Full details in the CHANGELOG.
