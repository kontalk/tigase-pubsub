\i database/postgresql-pubsub-schema-3.0.0.sql

-- LOAD FILE: database/postgresql-pubsub-schema-3.0.0.sql

-- QUERY START:
create or replace function TigPubSubRemoveService(varchar(2049)) returns void as $$
	delete from tig_pubsub_items where node_id in (
		select n.node_id from tig_pubsub_nodes n 
			inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
			where sj.service_jid = $1);
	delete from tig_pubsub_affiliations where node_id in (
		select n.node_id from tig_pubsub_nodes n 
			inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
			where sj.service_jid = $1);
	delete from tig_pubsub_subscriptions where node_id in (
		select n.node_id from tig_pubsub_nodes n 
			inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
			where sj.service_jid = $1);
	delete from tig_pubsub_nodes where node_id in (
		select n.node_id from tig_pubsub_nodes n 
			inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
			where sj.service_jid = $1);
	delete from tig_pubsub_service_jids where service_jid = $1;
	delete from tig_pubsub_affiliations where jid_id in (select j.jid_id from tig_pubsub_jids j where j.jid = $1);
	delete from tig_pubsub_subscriptions where jid_id in (select j.jid_id from tig_pubsub_jids j where j.jid = $1);
$$ LANGUAGE SQL;
-- QUERY END:
