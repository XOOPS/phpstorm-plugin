-- demo install
CREATE TABLE xoops_demo_bad (
  id INT NOT NULL
) ENGINE=MyISAM;

CREATE TABLE demo_child (
  id INT NOT NULL,
  parent_id INT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_parent FOREIGN KEY (parent_id) REFERENCES demo_parent (id)
) ENGINE=InnoDB;
-- REFERENCES in a comment is ignored
