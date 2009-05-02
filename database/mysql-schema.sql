CREATE  TABLE IF NOT EXISTS tig_pubsub_nodes (
 `name` TEXT NOT NULL  /* Node name (unique). */ ,
 `name_sha1` CHAR(40) NOT NULL  /* SHA1 hash of node_name used for uniqueness */,
 `type` INT(1) NOT NULL  /* Node type (0:collection, 1:leaf). */ ,
 `title` VARCHAR(1000) NULL  /* A friendly name for the node. */ ,
 `description` TEXT NOT NULL  /* A description of the node. */ ,
 `creator` VARCHAR(2047) NULL  /* The JID of the node creator. */ ,
 `creation_date` DATETIME NULL  /* The datetime when the node was created. */ ,
 `configuration` TEXT NULL ,
 `affiliations` TEXT NULL ,
 PRIMARY KEY (`name_sha1`)
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;
/* This node table contains attributes which are common to both node types. */

CREATE  TABLE IF NOT EXISTS tig_pubsub_items (
 `node_name_sha1` CHAR(40) NOT NULL,
 `id` TEXT NOT NULL,
 `creation_date` DATETIME NULL,
 `publisher` VARCHAR(2047) NULL,
 `update_date` DATETIME NULL,
 `data` LONGBLOB NULL,
 PRIMARY KEY USING HASH (`node_name_sha1`, `id`(255)),
 INDEX (`node_name_sha1`),
 INDEX (`id`(255)),
 CONSTRAINT
  FOREIGN KEY (`node_name_sha1`)
  REFERENCES `tig_pubsub_nodes`(`name_sha1`)
  MATCH FULL
  ON DELETE CASCADE
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;
/* Items stored by persistent nodes. */

CREATE  TABLE IF NOT EXISTS tig_pubsub_subscriptions (
 `node_name_sha1` CHAR(40) NOT NULL,
 `index` BIGINT NOT NULL,
 `data` TEXT NULL,
 INDEX (`node_name_sha1`),
 INDEX (`node_name_sha1`,`index`),
 CONSTRAINT
  FOREIGN KEY (`node_name_sha1`)
  REFERENCES `tig_pubsub_nodes`(`name_sha1`)
  MATCH FULL
  ON DELETE CASCADE
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;
/* Node subscriptions. */
 

delimiter //

-- Create node
create procedure TigPubSubCreateNode(_node_name text, _node_type int(1), _node_creator varchar(2047), _node_conf text)
begin
  insert into tig_pubsub_nodes (name, name_sha1, type, creator, creation_date, configuration)
    values (_node_name, SHA1(_node_name), _node_type, _node_creator, now(), _node_conf);
end //

-- Remove node
create procedure TigPubSubRemoveNode(_node_name text)
begin
  delete from tig_pubsub_items where node_name_sha1 = SHA1(_node_name);
  delete from tig_pubsub_subscriptions where node_name_sha1 = SHA1(_node_name);
  delete from tig_pubsub_nodes where name_sha1 = SHA1(_node_name);
end //

-- Get item of the node
create procedure TigPubSubGetItem(_node_name text, _item_id text)
begin
  select data, publisher, creation_date, update_date
    from tig_pubsub_items where node_name_sha1 = SHA1(_node_name) AND id = _item_id;
end //

-- Write item of the node
create procedure TigPubSubWriteItem(_node_name text, _item_id text, _publisher varchar(2047), _item_data longblob)
begin
  if exists( select creation_date from tig_pubsub_items
               where node_name_sha1 = SHA1(_node_name) AND id = _item_id )
  then
    update tig_pubsub_items set publisher = _publisher, data = _item_data, update_date = now()
      where node_name_sha1 = SHA1(_node_name) AND id = _item_id ;
  else
    insert into tig_pubsub_items (node_name_sha1, id, creation_date, update_date, publisher, data)
      values (SHA1(_node_name), _item_id, now(), now(), _publisher, _item_data);
  end if;
end //

-- Delete item
create procedure TigPubSubDeleteItem(_node_name text, _item_id text)
begin
  delete from tig_pubsub_items where node_name_sha1 = SHA1(_node_name) AND id = _item_id ;
end //

-- Get node's item IDs
create procedure TigPubSubGetNodeItemsIds(_node_name text)
begin
  select id from tig_pubsub_items where node_name_sha1 = SHA1(_node_name) ;
end //

-- Get all nodes names
create procedure TigPubSubGetAllNodes()
begin
  select name from tig_pubsub_nodes;
end //

-- Delete all nodes
create procedure TigPubSubDeleteAllNodes()
begin
  delete from tig_pubsub_items;
  delete from tig_pubsub_subscriptions;
  delete from tig_pubsub_nodes;
end //

-- Set node configuration
create procedure TigPubSubSetNodeConfiguration(_node_name text, _node_conf text)
begin
  update tig_pubsub_nodes set configuration = _node_conf where name_sha1 = SHA1(_node_name);
end //

-- Set node affiliations
create procedure TigPubSubSetNodeAffiliations(_node_name text, _node_aff text)
begin
  update tig_pubsub_nodes set affiliations = _node_aff where name_sha1 = SHA1(_node_name);
end //

-- Get node configuration
create procedure TigPubSubGetNodeConfiguration(_node_name text)
begin
  select configuration from tig_pubsub_nodes where name_sha1 = SHA1(_node_name);
end //

-- Get node affiliations
create procedure TigPubSubGetNodeAffiliations(_node_name text)
begin
  select affiliations from tig_pubsub_nodes where name_sha1 = SHA1(_node_name);
end //

-- Get node subscriptions
create procedure TigPubSubGetNodeSubscriptions(_node_name text)
begin
  select data from tig_pubsub_subscriptions where node_name_sha1 = SHA1(_node_name) order by `index` ;
end //

-- Set node subscription
create procedure TigPubSubSetNodeSubscriptions(_node_name text, _node_index bigint, _node_data text)
begin
  if exists( select `index` from tig_pubsub_subscriptions
               where node_name_sha1 = SHA1(_node_name) AND `index` = _node_index )
  then
    update tig_pubsub_subscriptions set data = _node_data
      where node_name_sha1 = SHA1(_node_name) AND `index` = _node_index ;
  else
    insert into tig_pubsub_subscriptions (node_name_sha1, `index`, data)
      values (SHA1(_node_name), _node_index, _node_data);
  end if;
end //

-- Delete node subscription
create procedure TigPubSubDeleteNodeSubscriptions(_node_name text, _node_index bigint)
begin
  delete from tig_pubsub_subscriptions where node_name_sha1 = SHA1(_node_name) AND `index` = _node_index ;
end //


delimiter ;
