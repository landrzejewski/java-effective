package pl.training.concurrency.extras.chat_v2;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class SynchronizedServiceWorkers implements ServerWorkers {

    private final ServerWorkers serverWorkers;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    SynchronizedServiceWorkers(ServerWorkers serverWorkers) {
        this.serverWorkers = serverWorkers;
    }

    @Override
    public void add(Worker worker) {
        lock.writeLock().lock();
        serverWorkers.add(worker);
        lock.writeLock().unlock();
    }

    @Override
    public void remove(Worker worker) {
        lock.writeLock().lock();
        serverWorkers.remove(worker);
        lock.writeLock().unlock();
    }

    @Override
    public void broadcast(String text) {
        lock.readLock().lock();
        serverWorkers.broadcast(text);
        lock.readLock().unlock();
    }

}
