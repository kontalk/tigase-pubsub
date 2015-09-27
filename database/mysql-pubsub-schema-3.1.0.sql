-- QUERY START:
source database/mysql-pubsub-schema-3.0.0.sql;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubRemoveService;
-- QUERY END:

delimiter //

-- QUERY START:
create procedure TigPubSubRemoveService(_service_jid varchar(2049))
begin
	declare _service_id bigint;
	DECLARE exit handler for sqlexception
		BEGIN
			-- ERROR
		ROLLBACK;
	END;

	START TRANSACTION;

	select service_id into _service_id from tig_pubsub_service_jids 
		where service_jid_sha1 = SHA1(_service_jid) and service_jid = _service_jid;
	delete from tig_pubsub_items where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = _service_id);
	delete from tig_pubsub_affiliations where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = _service_id);
	delete from tig_pubsub_subscriptions where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = _service_id);
	delete from tig_pubsub_nodes where service_id = _service_id;
	delete from tig_pubsub_service_jids where service_id = _service_id;
	delete from tig_pubsub_affiliations where jid_id in (select j.jid_id from tig_pubsub_jids j where j.jid_sha1 = SHA1(_service_jid) and j.jid = _service_jid);
	delete from tig_pubsub_subscriptions where jid_id in (select j.jid_id from tig_pubsub_jids j where j.jid_sha1 = SHA1(_service_jid) and j.jid = _service_jid);
	COMMIT;
end //
-- QUERY END:

delimiter ;
