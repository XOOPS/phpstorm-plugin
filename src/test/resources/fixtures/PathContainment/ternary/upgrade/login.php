<?php
// XOOPS upgrade login: realpath() behind a failure ternary is still a resolved path.

$languageRoot = realpath(XOOPS_ROOT_PATH . '/language');
$authFile = false !== $languageRoot
    ? realpath($languageRoot . DIRECTORY_SEPARATOR . 'english' . DIRECTORY_SEPARATOR . 'auth.php')
    : false;
if (false !== $authFile && str_starts_with($authFile, $languageRoot . DIRECTORY_SEPARATOR)) {
    include_once $authFile;
}

$avatarRootPath   = XOOPS_UPLOAD_PATH . '/avatars';
$avatarRootPrefix = is_dir($avatarRootPath)
    ? rtrim((string) realpath($avatarRootPath), DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR
    : '';
$avatarParentDir = realpath(dirname($avatarCandidate));
if ('' !== $avatarRootPrefix && str_starts_with(rtrim($avatarParentDir, DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR, $avatarRootPrefix)) {
    unlink($avatarCandidate);
}

// Not resolved: the ternary's real branch is a plain concatenation.
$loosePath = false !== $languageRoot ? $languageRoot . '/x/' . $name : false;
if (str_starts_with($loosePath, $languageRoot . DIRECTORY_SEPARATOR)) {
    include_once $loosePath;
}
