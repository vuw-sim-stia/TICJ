
CREATE SEQUENCE seq_soci_am
  INCREMENT 1
  MINVALUE 1
  MAXVALUE 9223372036854775807
  START 1
  CACHE 1;


CREATE TABLE nodes
(
  id bigint NOT NULL DEFAULT nextval('seq_soci_am'::regclass),
  uri character varying(2048),
  dpub timestamp without time zone,
  interactions text, -- list of comma separated values representing all individual backlinks from this node to others (e.g. hashtags or hyperlinks)
  CONSTRAINT pk_nodes PRIMARY KEY (id)
)
WITH (
  OIDS=FALSE
);

CREATE TABLE cascades
(
  id bigint NOT NULL DEFAULT nextval('seq_soci_am'::regclass),
  path_cnt bigint,
  CONSTRAINT cascades_pkey PRIMARY KEY (id)
)
WITH (
  OIDS=FALSE
);

CREATE TABLE identifier_sets
(
  id bigint NOT NULL DEFAULT nextval('seq_soci_am'::regclass),
  set text,
  identifier_set_index_global bigint,
  CONSTRAINT identifier_sets_pkey PRIMARY KEY (id)
)
WITH (
  OIDS=FALSE
);

CREATE TABLE identifiers
(
  id bigint NOT NULL DEFAULT nextval('seq_soci_am'::regclass),
  label character varying(2048),
  CONSTRAINT identifiers_pkey PRIMARY KEY (id)
)
WITH (
  OIDS=FALSE
);

CREATE TABLE links
(
  id bigint NOT NULL DEFAULT nextval('seq_soci_am'::regclass),
  source_node bigint,
  target_node bigint,
  interaction text,
  source_node_uri character varying(2048),
  target_node_uri character varying(2048),
  CONSTRAINT links_pkey PRIMARY KEY (id),
  CONSTRAINT fk_links_source_node FOREIGN KEY (source_node)
      REFERENCES nodes (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT fk_links_target_node FOREIGN KEY (target_node)
      REFERENCES nodes (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION
)
WITH (
  OIDS=FALSE
);

CREATE INDEX fki_links_source_node
  ON links
  USING btree
  (source_node);

CREATE INDEX fki_links_target_node
  ON links
  USING btree
  (target_node);

CREATE INDEX target_index
  ON links
  USING btree
  (target_node_uri COLLATE pg_catalog."default" varchar_ops);


CREATE TABLE nodes_cascades
(
  nodes_id bigint,
  cascades_id bigint,
  node_uri character varying(2048)
)
WITH (
  OIDS=FALSE
);

CREATE TABLE nodes_identifier_sets
(
  node_id bigint NOT NULL,
  identifier_set_id bigint NOT NULL,
  identifier_set_index bigint,
  CONSTRAINT nodes_identifer_sets_pkey PRIMARY KEY (node_id, identifier_set_id)
)
WITH (
  OIDS=FALSE
);

CREATE TABLE nodes_identifiers
(
  node_id bigint NOT NULL,
  identifier_id bigint NOT NULL,
  identifier_index bigint,
  CONSTRAINT nodes_identifiers_pkey PRIMARY KEY (node_id, identifier_id)
)
WITH (
  OIDS=FALSE
);

CREATE TABLE roots
(
  id bigint NOT NULL DEFAULT nextval('seq_soci_am'::regclass),
  interaction text,
  root_node_uri character varying(2048),
  CONSTRAINT roots_pkey PRIMARY KEY (id)
)
WITH (
  OIDS=FALSE
);

CREATE OR REPLACE VIEW node_coordinates AS 
 SELECT ids.identifier_set_index_global,
    nids.identifier_set_index,
    n.dpub,
    n.uri
   FROM nodes n
     JOIN nodes_identifier_sets nids ON n.id = nids.node_id
     JOIN identifier_sets ids ON nids.identifier_set_id = ids.id
  ORDER BY n.dpub;

CREATE TABLE content
(
  node_id bigint,
  node_uri character varying(2048),
  content text
)
WITH (
  OIDS=FALSE
);