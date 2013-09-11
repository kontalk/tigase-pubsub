-- QUERY START:
if not exists (select * from sysobjects where name='tig_pubsub_nodes' and xtype='U')
	CREATE  TABLE [dbo].[tig_pubsub_nodes] (
		[service_jid] nvarchar(2049) NOT NULL  /* Service JID */,
		[service_jid_sha1] [varbinary](40) NOT NULL  /* SHA1 hash of service_jid_sha1 */,
		[service_jid_index] AS CAST( [service_jid] AS NVARCHAR(255)),
		[name] TEXT NOT NULL  /* Node name (unique). */ ,
		[name_sha1] [varbinary](40) NOT NULL  /* SHA1 hash of node_name used for uniqueness */,
		[name_index] AS CAST( [name] AS NVARCHAR(255)),
		[type] INT NOT NULL  /* Node type (0:collection, 1:leaf). */ ,
		[title] nvarchar(1000) NULL  /* A friendly name for the node. */ ,
		[description] TEXT  /* A description of the node. */ ,
		[creator] nvarchar(2047) NULL  /* The JID of the node creator. */ ,
		[creation_date] DATETIME NULL  /* The datetime when the node was created. */ ,
		[configuration] NTEXT NULL ,
		[affiliations] NTEXT NULL ,
		PRIMARY KEY ( [service_jid_sha1], [name_sha1] ),
	);
-- QUERY END:
GO
	
-- QUERY START:
CREATE INDEX IX_tig_pubsub_nodes_service_jid ON [dbo].[tig_pubsub_nodes](service_jid_index);
-- QUERY END:
GO

-- QUERY START:
CREATE INDEX IX_tig_pubsub_nodes_name ON [dbo].[tig_pubsub_nodes](name_index);
-- QUERY END:
GO

/* This node table contains attributes which are common to both node types. */

-- QUERY START:
if not exists (select * from sysobjects where name='tig_pubsub_items' and xtype='U')
	CREATE  TABLE [dbo].[tig_pubsub_items] (
		[service_jid_sha1] [varbinary](40) NOT NULL,
		[node_name_sha1] [varbinary](40) NOT NULL,
		[id] nvarchar(MAX) NOT NULL,
		[creation_date] DATETIME NULL,
		[publisher] nvarchar(2047) NULL,
		[update_date] DATETIME NULL,
		[data] NTEXT NULL,
		[id_index] AS CAST( [id] AS NVARCHAR(255)),
		[pk_node_id_hash] AS CAST( HASHBYTES('SHA1', CONVERT( nvarchar(max), [service_jid_sha1],2) + CONVERT( nvarchar(max), [node_name_sha1],2) + [id]) AS VARBINARY(40)) PERSISTED,

		PRIMARY KEY  ( [pk_node_id_hash] ),

		CONSTRAINT [FK_tig_pubsub_items_tig_pubsub_nodes] FOREIGN KEY ([service_jid_sha1], [node_name_sha1])
		REFERENCES [dbo].[tig_pubsub_nodes]([service_jid_sha1], [name_sha1])
		ON DELETE CASCADE
 
)
-- QUERY END:
GO

-- QUERY START:
CREATE INDEX [IX_tig_pubsub_items_id_index] ON [dbo].[tig_pubsub_items]([id_index]);
-- QUERY END:
GO

/* Items stored by persistent nodes. */
-- QUERY START:
if not exists (select * from sysobjects where name='tig_pubsub_subscriptions' and xtype='U')
	CREATE  TABLE [dbo].[tig_pubsub_subscriptions] (

 [service_jid_sha1] [varbinary](40) NOT NULL,
 [node_name_sha1] [varbinary](40) NOT NULL,
 [index] bigint NOT NULL,
 [data] NTEXT NULL,
 
 PRIMARY KEY  ( [service_jid_sha1],[node_name_sha1],[index] ),
 CONSTRAINT [FK_tig_pubsub_subscriptions_tig_pubsub_nodes] FOREIGN KEY ([service_jid_sha1],[node_name_sha1])
 REFERENCES [dbo].[tig_pubsub_nodes]([service_jid_sha1],[name_sha1])
 ON DELETE CASCADE
 
)
-- QUERY END:
GO

/* Node subscriptions. */

-- QUERY START:
-- Create node
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubCreateNode')
DROP PROCEDURE TigPubSubCreateNode
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubCreateNode
	@_service_jid nvarchar(2049), 
	@_node_name nvarchar(max),
	@_node_type int,
	@_node_creator nvarchar(2047),
	@_node_conf ntext
AS	
begin
	insert into tig_pubsub_nodes (service_jid, service_jid_sha1, name, name_sha1, type, creator, creation_date, configuration)
		values (@_service_jid, HASHBYTES('SHA1', @_service_jid), @_node_name, HASHBYTES('SHA1', @_node_name), @_node_type, @_node_creator, getdate(), @_node_conf);
end
-- QUERY END:
GO


-- QUERY START:
-- Remove node
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubRemoveNode')
DROP PROCEDURE [dbo].[TigPubSubRemoveNode]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[TigPubSubRemoveNode]
	@_service_jid nvarchar(2049), 
	@_node_name nvarchar(MAX)
AS	
begin
  delete from tig_pubsub_items where service_jid_sha1 = HASHBYTES('SHA1', @_service_jid) AND node_name_sha1 = HASHBYTES('SHA1', @_node_name);
  delete from tig_pubsub_subscriptions where service_jid_sha1 = HASHBYTES('SHA1', @_service_jid) AND node_name_sha1 = HASHBYTES('SHA1', @_node_name);
  delete from tig_pubsub_nodes where service_jid_sha1 = HASHBYTES('SHA1', @_service_jid) AND name_sha1 = HASHBYTES('SHA1', @_node_name);

end
-- QUERY END:
GO

-- QUERY START:
-- Get item of the node
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetItem')
DROP PROCEDURE [dbo].[TigPubSubGetItem]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[TigPubSubGetItem]
	@_service_jid nvarchar(2049), 
	@_node_name nvarchar(MAX),
	@_item_id nvarchar(MAX)
AS	
begin
  select data, publisher, creation_date, update_date
    from tig_pubsub_items where service_jid_sha1 = HASHBYTES('SHA1', @_service_jid) AND node_name_sha1 = HASHBYTES('SHA1', @_node_name) AND id = @_item_id;

end
-- QUERY END:
GO

-- QUERY START:
-- Write item of the node
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubWriteItem')
DROP PROCEDURE [dbo].[TigPubSubWriteItem]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[TigPubSubWriteItem]
	@_service_jid nvarchar(2049), 
	@_node_name nvarchar(MAX),
	@_item_id nvarchar(MAX),
	@_publisher nvarchar(2047),
	@_item_data ntext
AS	
begin
    SET NOCOUNT ON;
	-- Update the row if it exists.    
    UPDATE [dbo].[tig_pubsub_items] 
		SET publisher = @_publisher, data = @_item_data, update_date = getdate()
		WHERE [dbo].[tig_pubsub_items].pk_node_id_hash = (HASHBYTES('SHA1', CONVERT( nvarchar(max), [service_jid_sha1],2) +  CONVERT(nvarchar(max), HASHBYTES('SHA1',@_node_name) ,2)  + @_item_id))
	-- Insert the row if the UPDATE statement failed.	
	IF (@@ROWCOUNT = 0 )
	BEGIN
		insert into  [dbo].[tig_pubsub_items]  (service_jid_sha1, node_name_sha1, id, creation_date, update_date, publisher, data)
		values (HASHBYTES('SHA1', @_service_jid), HASHBYTES('SHA1', @_node_name), @_item_id, getdate(), getdate(), @_publisher, @_item_data)
	END
end
-- QUERY END:
GO


-- QUERY START:
-- Delete item
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubDeleteItem')
DROP PROCEDURE [dbo].[TigPubSubDeleteItem]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[TigPubSubDeleteItem]
	@_service_jid nvarchar(2049), 
	@_node_name nvarchar(MAX),
	@_item_id nvarchar(MAX)
AS	
begin
	delete from tig_pubsub_items where service_jid_sha1 = HASHBYTES('SHA1', @_service_jid) AND node_name_sha1 = HASHBYTES('SHA1', @_node_name) AND id = @_item_id ;
end
-- QUERY END:
GO

-- QUERY START:
-- Get node's item IDs
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetNodeItemsIds')
DROP PROCEDURE [dbo].[TigPubSubGetNodeItemsIds]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[TigPubSubGetNodeItemsIds]
	@_service_jid nvarchar(2049), 
	@_node_name nvarchar(MAX)
AS	
begin
	select id from tig_pubsub_items where service_jid_sha1 = HASHBYTES('SHA1', @_service_jid) AND node_name_sha1 = HASHBYTES('SHA1', @_node_name) ;
end
-- QUERY END:
GO

-- QUERY START:
-- Get all nodes names
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetAllNodes')
DROP PROCEDURE [dbo].[TigPubSubGetAllNodes]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[TigPubSubGetAllNodes]
	@_service_jid nvarchar(2049)
AS	
begin
	select name from tig_pubsub_nodes where service_jid_sha1 = HASHBYTES('SHA1', @_service_jid);
end
-- QUERY END:
GO

-- QUERY START:
-- Delete all nodes
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubDeleteAllNodes')
DROP PROCEDURE [dbo].[TigPubSubDeleteAllNodes]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[TigPubSubDeleteAllNodes]
	@_service_jid nvarchar(2049)
AS	
begin
  delete from tig_pubsub_items where service_jid_sha1 = HASHBYTES('SHA1', @_service_jid);
  delete from tig_pubsub_subscriptions where service_jid_sha1 = HASHBYTES('SHA1', @_service_jid);
  delete from tig_pubsub_nodes where service_jid_sha1 = HASHBYTES('SHA1', @_service_jid);
end
-- QUERY END:
GO

-- QUERY START:
-- Set node configuration
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubSetNodeConfiguration')
DROP PROCEDURE [dbo].[TigPubSubSetNodeConfiguration]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[TigPubSubSetNodeConfiguration]
	@_service_jid nvarchar(2049), 
	@_node_name nvarchar(MAX),
	@_node_conf ntext
AS	
begin
  update tig_pubsub_nodes set configuration = @_node_conf where service_jid_sha1 = HASHBYTES('SHA1', @_service_jid) AND name_sha1 =  HASHBYTES('SHA1', @_node_name);
end
-- QUERY END:
GO


-- QUERY START:
-- Set node affiliations
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubSetNodeAffiliations')
DROP PROCEDURE [dbo].[TigPubSubSetNodeAffiliations]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[TigPubSubSetNodeAffiliations]
	@_service_jid nvarchar(2049),
	@_node_name nvarchar(MAX),
	@_node_aff ntext
AS
begin
  update tig_pubsub_nodes set affiliations = @_node_aff where service_jid_sha1 = HASHBYTES('SHA1', @_service_jid) AND name_sha1 = HASHBYTES('SHA1', @_node_name);
end
-- QUERY END:
GO

-- QUERY START:
-- Get node configuration
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetNodeConfiguration')
DROP PROCEDURE [dbo].[TigPubSubGetNodeConfiguration]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[TigPubSubGetNodeConfiguration]
	@_service_jid nvarchar(2049),
	@_node_name nvarchar(MAX)
AS
begin
  select configuration from tig_pubsub_nodes where service_jid_sha1 = HASHBYTES('SHA1', @_service_jid) AND name_sha1 = HASHBYTES('SHA1', @_node_name);
end
-- QUERY END:
GO

-- QUERY START:
-- Get node affiliations
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetNodeAffiliations')
DROP PROCEDURE [dbo].[TigPubSubGetNodeAffiliations]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[TigPubSubGetNodeAffiliations]
	@_service_jid nvarchar(2049),
	@_node_name nvarchar(MAX)
AS
begin
  select affiliations from tig_pubsub_nodes where service_jid_sha1 = HASHBYTES('SHA1', @_service_jid) AND name_sha1 = HASHBYTES('SHA1', @_node_name);
end
-- QUERY END:
GO

-- QUERY START:
-- Get node subscriptions
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetNodeSubscriptions')
DROP PROCEDURE [dbo].[TigPubSubGetNodeSubscriptions]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[TigPubSubGetNodeSubscriptions]
	@_service_jid nvarchar(2049),
	@_node_name nvarchar(MAX)
AS
begin
  select data from tig_pubsub_subscriptions where service_jid_sha1 = HASHBYTES('SHA1', @_service_jid) AND node_name_sha1 = HASHBYTES('SHA1', @_node_name) order by [index] ;
end
-- QUERY END:
GO

-- QUERY START:
-- Set node subscription
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubSetNodeSubscriptions')
DROP PROCEDURE [dbo].[TigPubSubSetNodeSubscriptions]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[TigPubSubSetNodeSubscriptions]
	@_service_jid nvarchar(2049),
	@_node_name nvarchar(MAX),
	@_node_index bigint,
	@_node_data ntext
AS	
begin
    SET NOCOUNT ON;
	-- Update the row if it exists.    
    UPDATE [dbo].[tig_pubsub_subscriptions] 
		SET data = @_node_data
		WHERE [dbo].[tig_pubsub_subscriptions].node_name_sha1 = ( HASHBYTES('SHA1',@_node_name) )
	-- Insert the row if the UPDATE statement failed.	
	IF (@@ROWCOUNT = 0 )
	BEGIN
		insert into  [dbo].[tig_pubsub_subscriptions]  ([service_jid_sha1], [node_name_sha1], [index], [data])
		values (HASHBYTES('SHA1', @_service_jid), HASHBYTES('SHA1', @_node_name), @_node_index, @_node_data)
	END
end
-- QUERY END:
GO

-- QUERY START:
-- Delete node subscription
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubDeleteNodeSubscriptions')
DROP PROCEDURE [dbo].[TigPubSubDeleteNodeSubscriptions]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[TigPubSubDeleteNodeSubscriptions]
	@_service_jid nvarchar(2049),
	@_node_name nvarchar(MAX),
	@_node_index bigint
AS
begin
  delete from tig_pubsub_subscriptions where service_jid_sha1 = HASHBYTES('SHA1', @_service_jid) AND node_name_sha1 = HASHBYTES('SHA1', @_node_name) AND [index] = @_node_index ;
end
-- QUERY END:
GO
