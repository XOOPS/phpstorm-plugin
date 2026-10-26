<?php

declare(strict_types=1);

namespace XoopsModules\Publisher\Versioncontrol;

/**
 * MySQL-Implementation of version control Package
 * by Cornelius Bolten, 2006.
 *
 * This is an example of an MySQL-Implementation of the
 * version control package available on phpclasses.org.
 *
 * the abstract methods must be filled in each implementation.
 *
 * check the code and the method-documentation for required
 * informations.
 *
 * this code is not just a hack, please be patient of errors.
 * fixes and increments please to me@homeofevil.com. thanks a lot!
 *
 * required table-structure:
 *
 * CREATE TABLE sys_versioncontrol (
 * id                  integer(11)     autoincrement NOT NULL,
 * object_identifier   VARCHAR(250)    NOT NULL,
 * object_version      INTEGER(5)      NOT NULL    default '1',
 * object_editor       INTEGER(5),
 * object_date         INTEGER(11),
 * object_data         longtext        NOT NULL,
 * object_hash         VARCHAR(32)     NOT NULL,
 * object_informations longtext,
 * PRIMARY KEY (id),
 * UNIQUE (
 * object_identifier,
 * object_version,
 * object_hash
 * )
 * )
 *
 * CREATE TABLE IF NOT EXISTS `sys_versioncontrol` (
 * `id` int(11) NOT NULL AUTO_INCREMENT,
 * `object_identifier` varchar(250) NOT NULL,
 * `object_version` int(5) NOT NULL DEFAULT '1',
 * `object_editor` int(5) DEFAULT NULL,
 * `object_date` int(11) DEFAULT NULL,
 * `object_data` longtext NOT NULL,
 * `object_hash` varchar(32) NOT NULL,
 * `object_informations` longtext,
 * PRIMARY KEY (`id`),
 * UNIQUE KEY `object_identifier` (`object_identifier`,`object_version`,`object_hash`)
 * ) ENGINE=InnoDB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1 ;
 *
 * Usage is free for non-commercial work.
 * For feedback, bug reports or enhancements please contact the author at
 * me@homeofevil.com. Thanks a lot!
 *
 * The Initial Developer of the Original Code is Cornelius Bolten.
 * Portions created by Cornelius Bolten are Copyright (C) 2004 by Cornelius Bolten.
 * All Rights Reserved.
 */

//use XoopsModules\Publisher\Versioncontrol;

/*
 * @author     $Author: Cornelius Bolten $
 * @version    $Revision: 1.0 $
 * @package    VersionControl06
 * @link       http://www.phpclasses.org/browse/package/2803.html  latest available version @ phpclasses.org
 */

/**
 * Class VersionControlMySQL
 */
final class VersionControlMySQL extends VersionControl
{
    //    private $oMySQLConnection;
    private $xoopsDB;

    /**
     * constructor
     * call parent constructor and
     * connect to the database
     * @param \XoopsDatabase|\XoopsMySQLDatabase|null $xoopsDB
     */
    public function __construct(\XoopsDatabase $xoopsDB = null)
    {
        parent::__construct();
        //        $this->MySQLConnection = mysql_connect('mysql_host', 'mysql_user', 'mysql_password')
        //        or throw new RuntimeException($this->xoopsDB->error());
        //        mysqli_select_db($this->xoopsDB->conn, 'my_database')
        //        or throw new Exception('Could not select database');

        if (null === $xoopsDB) {
            $xoopsDB = \XoopsDatabaseFactory::getDatabaseConnection();
        }

        $this->xoopsDB = $xoopsDB;
    }

    /**
     * send a query to the mysql-database
     *
     * @param string $sql
     * @return  array   fetched mysq-data
     */
    public function query($sql)
    {
        $aData    = [];
        $iCounter = 0;

        $result = $this->xoopsDB->query($sql);
        if (!$this->xoopsDB->isResultSet($result)) {
            while (false !== ($aRow = $this->xoopsDB->fetchBoth($result))) {
                $aData[$iCounter] = $aRow;
                ++$iCounter;
            }

            if ($iCounter <= 0) {
                throw new \RuntimeException('Counter < 0, no records found');
            }

            $this->xoopsDB->freeRecordSet($result);

            return $aData;
        }

        throw new \RuntimeException('No records found');
    }

    /**
     * return the internal id of the
     * latest version of a stored object
     *
     * @param string $sIdentifier
     * @return int internal id
     * @throws \Exception
     */
    public function getLatestVersionId($sIdentifier)
    {
        //        $sql  = "
        //            SELECT  id
        //            FROM    " . $this->xoopsDB->prefix('publisher_versioncontrol') . '
        //            WHERE   object_identifier = ' . $sIdentifier . '
        //            AND     object_version = (
        //                SELECT  max(object_version)
        //                FROM    ' . $this->xoopsDB->prefix('publisher_versioncontrol') . '
        //                WHERE   object_identifier = ' . $sIdentifier . '
        //            )
        //        ';

        $sql = '
            SELECT  id
            FROM    ' . $this->xoopsDB->prefix('publisher_versioncontrol') . "
            WHERE   object_identifier = '$sIdentifier'
            AND     object_version = (
                SELECT  max(object_version)
                FROM    " . $this->xoopsDB->prefix('publisher_versioncontrol') . "
                WHERE   object_identifier = '$sIdentifier'
            )
        ";

        $result = $this->query($sql);

        return $result[0]['id'];
    }

    /**
     * return the latest version number of a stored object
     *
     * @param string $sIdentifier
     * @return int version
     * @throws \Exception
     */
    public function getLatestVersionNo($sIdentifier)
    {
        $sql  = '
            SELECT  max(object_version) AS object_version
            FROM    ' . $this->xoopsDB->prefix('publisher_versioncontrol') . '
            WHERE   object_identifier = ' . $sIdentifier . '
        ';
        $result = $this->query($sql);

        return $result[0]['object_version'];
    }

    /**
     * return the latest version of a stored object
     *
     * @param string $sIdentifier
     * @return int version
     * @throws \Exception
     */
    public function getLatestVersion($sIdentifier)
    {
        //        $sql  = '
        //            SELECT  id, object_identifier, object_version,
        //                    object_editor, object_date, object_data, object_hash,
        //                    object_informations
        //            FROM    ' . $this->xoopsDB->prefix('publisher_versioncontrol') . '
        //            WHERE   object_identifier = ' . $sIdentifier . '
        //            AND     object_version = (
        //                SELECT  max(object_version)
        //                FROM    ' . $this->xoopsDB->prefix('publisher_versioncontrol') . '
        //                WHERE   object_identifier = ' . $sIdentifier . '
        //            )
        //        ';

        $sql = '
            SELECT  id, object_identifier, object_version,
                    object_editor, object_date, object_data, object_hash,
                    object_informations
            FROM    ' . $this->xoopsDB->prefix('publisher_versioncontrol') . "
            WHERE   object_identifier = '$sIdentifier'
            AND     object_version = (
                SELECT  max(object_version)
                FROM    " . $this->xoopsDB->prefix('publisher_versioncontrol') . "
                WHERE   object_identifier = '$sIdentifier'
            )
        ";

        $result = $this->query($sql);

        return $result[0];
    }

    /**
     * return a version of a stored object
     *
     * @param string $sIdentifier
     * @param int    $iVersionNo
     * @return int version
     * @throws \Exception
     */
    public function getVersion($sIdentifier, $iVersionNo)
    {
        //        $sql  = "
        //            SELECT  id, object_identifier, object_version,
        //                    object_editor, object_date, object_data, object_hash,
        //                    object_informations
        //            FROM    " . $this->xoopsDB->prefix('publisher_versioncontrol') . '
        //            WHERE   object_identifier = ' . $sIdentifier . '
        //            AND     object_version = ' . $iVersionNo;

        $sql = '
            SELECT  id, object_identifier, object_version,
                    object_editor, object_date, object_data, object_hash,
                    object_informations
            FROM    ' . $this->xoopsDB->prefix('publisher_versioncontrol') . "
            WHERE   object_identifier = '$sIdentifier'
            AND     object_version = '$sIdentifier'
            )
        ";

        $result = $this->query($sql);

        return $result[0];
    }

    /**
     * returns all versions of a stored object
     *
     * @param string $sIdentifier
     */
    public function getAllVersion($sIdentifier): void
    {
        throw new \RuntimeException('this abstract method needs to be implemented..');
    }

    /**
     * check if a given hash already exists in the repository
     *
     * @param string $sIdentifier
     * @param string $sHash
     * @return bool
     * @throws \Exception
     */
    public function objectHashExists($sIdentifier, $sHash)
    {
        //        $sql  = "
        //            SELECT  id
        //            FROM    " . $this->xoopsDB->prefix('publisher_versioncontrol') . '
        //            WHERE   object_identifier = ' . $sIdentifier . '
        //            AND     object_hash = ' . $sHash;

        $sql = '
            SELECT  id
            FROM    ' . $this->xoopsDB->prefix('publisher_versioncontrol') . "
            WHERE   object_identifier = '$sIdentifier'
            AND     object_hash ='$sHash'
            ";

        $result = $this->query($sql);
        if ($result[0]['id'] >= 1) {
            return true;
        }

        return false;
    }

    /**
     * write a new record to the versioncontrol-repository
     *
     * @param string $sIdentifier
     * @param int    $iDate
     * @param int    $iNextVersionNo
     * @param string $sResultData
     * @param string $sResultHash
     * @param string $iEditor
     * @param string $sInformations
     * @return bool
     * @throws \Exception
     */
    public function write($sIdentifier, $iDate, $iNextVersionNo, $sResultData, $sResultHash, $iEditor, $sInformations)
    {
        $sql = '
            INSERT INTO ' . $this->xoopsDB->prefix('publisher_versioncontrol') . ' (
                object_identifier, object_version, object_editor, object_date,
                object_data, object_hash, object_informations
            ) VALUES (
                ' . "$sIdentifier, $iNextVersionNo, $iEditor, $iDate, $sResultData, $sResultHash, $sInformations" . '
            )
        ';
        $this->query($sql);

        return true;
    }
}
