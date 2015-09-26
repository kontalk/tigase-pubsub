-- QUERY START:
SET QUOTED_IDENTIFIER ON
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubRemoveService')
	DROP PROCEDURE TigPubSubRemoveService
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubRemoveService
	@_service_jid nvarchar(2049)
AS	
begin
	declare @_service_id bigint;
	
	select @_service_id=service_id from tig_pubsub_service_jids where service_jid_sha1 = HASHBYTES('SHA1', @_service_jid);
  
	delete from dbo.tig_pubsub_items where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = @_service_id);
	delete from dbo.tig_pubsub_subscriptions where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = @_service_id);;
	delete from dbo.tig_pubsub_affiliations where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = @_service_id);
	delete from dbo.tig_pubsub_nodes where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = @_service_id);
	delete from tig_pubsub_service_jids where service_id = @_service_id;
	delete from tig_pubsub_affiliations where jid_id in (
		select j.jid_id from tig_pubsub_jids j where j.jid_sha1 = HASHBYTES('SHA1', @_service_jid) and j.jid = @_service_jid);
	delete from tig_pubsub_subscriptions where jid_id in (
		select j.jid_id from tig_pubsub_jids j where j.jid_sha1 = HASHBYTES('SHA1', @_service_jid) and j.jid = @_service_jid);
end
-- QUERY END:
GO

