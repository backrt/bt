package bt.torrent.messaging;

import bt.net.IMessageDispatcher;
import bt.net.Peer;
import bt.protocol.Message;
import bt.torrent.IPieceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TorrentWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(TorrentWorker.class);

    private IPieceManager pieceManager;
    private IMessageDispatcher dispatcher;

    private Set<MessageConsumer> messageConsumers;
    private Set<MessageProducer> messageProducers;

    private ConcurrentMap<Peer, MessageWorker> peerMap;

    public TorrentWorker(IPieceManager pieceManager, IMessageDispatcher dispatcher,
                         Set<MessageConsumer> messageConsumers, Set<MessageProducer> messageProducers) {
        this.pieceManager = pieceManager;
        this.dispatcher = dispatcher;
        this.messageConsumers = messageConsumers;
        this.messageProducers = messageProducers;
        this.peerMap = new ConcurrentHashMap<>();
    }

    public void addPeer(Peer peer) {
        MessageWorker worker = new MessageWorker(peer, messageConsumers, messageProducers);
        MessageWorker existing = peerMap.putIfAbsent(peer, worker);
        if (existing == null) {
            dispatcher.addMessageConsumer(peer, worker::accept);
            dispatcher.addMessageSupplier(peer, worker::get);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Added connection for peer: " + peer);
            }
        }
    }

    public void removePeer(Peer peer) {
        MessageWorker removed = peerMap.remove(peer);
        if (removed != null) {
            Optional<Integer> assignedPiece = pieceManager.getAssignedPiece(peer);
            if (assignedPiece.isPresent()) {
                pieceManager.unselectPieceForPeer(peer, assignedPiece.get());
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Removed connection for peer: " + peer);
            }
        }
    }

    public Set<Peer> getPeers() {
        return peerMap.keySet();
    }

    public ConnectionState getConnectionState(Peer peer) {
        MessageWorker worker = peerMap.get(peer);
        return (worker == null) ? null : worker.getConnectionState();
    }

    // TODO: this is a hack, remove
    public void broadcast(Message message) {
        peerMap.values().forEach(worker -> worker.accept(message));
    }
}