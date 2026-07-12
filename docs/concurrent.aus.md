# file: concurrent.aus

## class: AtomicBool

[106:14] (extern: com.aussom.stdlib.AAtomicBool) **extends: object** 

Atomic boolean. The main use is the one-shot guard: many threads
race compareAndSet(false, true) and exactly one wins. Each method
call is atomic; a sequence of method calls is not.

#### Methods

- **AtomicBool** (`bool Initial = false`)

	> Creates a new AtomicBool.

	- **@p** `Initial` is an optional bool with the starting value. Default false.
	- **@r** `A` new AtomicBool object.


- **newAtomicBool** (`bool Initial = false`)


- **get** ()

	> Gets the current value.

	- **@r** `A` bool with the current value.


- **set** (`bool Value`)

	> Sets the value.

	- **@p** `Value` is a bool with the value to set.
	- **@r** `this` object


- **getAndSet** (`bool Value`)

	> Atomically sets Value and returns the previous value.

	- **@p** `Value` is a bool with the value to set.
	- **@r** `A` bool with the previous value.


- **compareAndSet** (`bool Expect, bool Update`)

	> Atomically sets Update only if the current value equals Expect. Exactly one of several racing callers wins.

	- **@p** `Expect` is a bool with the expected current value.
	- **@p** `Update` is a bool with the new value.
	- **@r** `A` bool with true when the swap happened.




## class: Semaphore

[315:14] (extern: com.aussom.stdlib.ASemaphore) **extends: object** 

Counting semaphore for throttling: at most N concurrent holders
of a resource. There is deliberately no timeout-free acquire so a
script cannot park a host worker thread forever; tryAcquire(0) is
the non-blocking form. Each method call is atomic; a sequence of
method calls is not.

#### Methods

- **Semaphore** (`int Permits = 1`)

	> Creates a new Semaphore.

	- **@p** `Permits` is an optional int with the number of permits. Default 1, which acts as a simple mutex.
	- **@r** `A` new Semaphore object.


- **newSemaphore** (`int Permits = 1`)


- **tryAcquire** (`int TimeoutMs = 0`)

	> Tries to take a permit, waiting up to TimeoutMs.

	- **@p** `TimeoutMs` is an optional int with how long to wait in milliseconds. Default 0 for no waiting.
	- **@r** `A` bool with true when a permit was acquired.


- **release** ()

	> Returns a permit. Call once per successful tryAcquire.

	- **@r** `this` object


- **availablePermits** ()

	> Gets the number of available permits.

	- **@r** `An` int with the available permits.




## class: Counter

[204:14] (extern: com.aussom.stdlib.ACounter) **extends: object** 

Striped counter built for write contention. Where AtomicLong
retries when many threads hammer it, Counter stripes writes
across cells and sums on read. The default choice for metrics
and hit counters. Trade-offs: no compareAndSet, and sum() is not
a point-in-time snapshot while writers are active. Each method
call is atomic; a sequence of method calls is not.

#### Methods

- **increment** ()

	> Adds one.

	- **@r** `this` object


- **decrement** ()

	> Subtracts one.

	- **@r** `this` object


- **add** (`int Delta`)

	> Adds Delta.

	- **@p** `Delta` is an int with the amount to add (may be negative).
	- **@r** `this` object


- **sum** ()

	> Returns the current total.

	- **@r** `An` int with the sum.


- **reset** ()

	> Resets the total to zero. Only reliable when no other thread is writing.

	- **@r** `this` object




## class: BlockingQueue

[249:14] (extern: com.aussom.stdlib.ABlockingQueue) **extends: object** 

Thread-safe FIFO queue with optional capacity and blocking
operations. The bridge between threads: many producers offer()
work items and one dedicated worker take()s them, owning any
non-thread-safe resources. A bounded queue gives backpressure.
Values cross threads safely through the queue; treat a handed-off
mutable value as given away. null cannot be queued because null
is poll()'s empty return. take() and put() block the calling
thread, so request handlers should prefer offer()/poll() with
timeouts and leave indefinite blocking to dedicated threads.

#### Methods

- **BlockingQueue** (`int Capacity = 0`)

	> Creates a new BlockingQueue.

	- **@p** `Capacity` is an optional int with the maximum number of queued values. Default 0 for unbounded.
	- **@r** `A` new BlockingQueue object.


- **newBlockingQueue** (`int Capacity = 0`)


- **offer** (`Value, int TimeoutMs = 0`)

	> Adds a value, waiting up to TimeoutMs for space.

	- **@p** `Value` is the value to queue. null is rejected.
	- **@p** `TimeoutMs` is an optional int with how long to wait for space in milliseconds. Default 0 for no waiting.
	- **@r** `A` bool with true when the value was queued.


- **poll** (`int TimeoutMs = 0`)

	> Removes and returns the head value, waiting up to TimeoutMs for one to arrive.

	- **@p** `TimeoutMs` is an optional int with how long to wait in milliseconds. Default 0 for no waiting.
	- **@r** `The` head value, or null when none arrived in time.


- **put** (`Value`)

	> Adds a value, blocking until space is available. Meant for dedicated worker threads, not request handlers.

	- **@p** `Value` is the value to queue. null is rejected.
	- **@r** `this` object


- **take** ()

	> Removes and returns the head value, blocking until one is available. Meant for dedicated worker threads, not request handlers.

	- **@r** `The` head value.


- **size** ()

	> Gets the number of queued values.

	- **@r** `An` int with the size.


- **clear** ()

	> Removes all queued values.

	- **@r** `this` object




## class: Lock

[398:14] (extern: com.aussom.stdlib.ALock) **extends: object** 

Scoped mutual exclusion. The escape hatch for multi-step updates
that no single atomic method covers. There is deliberately no
separate lock()/unlock() pair: a script exception between the two
would leak the lock forever, so the only way to hold the lock is
withLock, where acquire, run, and guaranteed release happen
inside one call. Prefer the atomic classes above when one of them
fits; two locks taken in different orders by different threads
can still deadlock (the wait timeout turns that into a visible
error instead of a frozen thread).

#### Methods

- **withLock** (`callback Cb, int WaitMs = 30000`)

	> Runs Cb while holding the lock and returns its return value. The lock is released when Cb finishes or throws. Reentrant: Cb may call withLock on the same Lock again.

	- **@p** `Cb` is a callback taking no arguments.
	- **@p** `WaitMs` is an optional int with how long to wait for the lock in milliseconds before throwing. Default 30000.
	- **@r** `The` callback's return value.




## class: AtomicLong

[41:14] (extern: com.aussom.stdlib.AAtomicLong) **extends: object** 

Atomic 64-bit integer. The workhorse for IDs and low-contention
counters. For metrics counters hammered by many writer threads,
prefer Counter. Each method call is atomic; a sequence of method
calls is not.

#### Methods

- **AtomicLong** (`int Initial = 0`)

	> Creates a new AtomicLong.

	- **@p** `Initial` is an optional int with the starting value. Default 0.
	- **@r** `A` new AtomicLong object.


- **newAtomicLong** (`int Initial = 0`)


- **get** ()

	> Gets the current value.

	- **@r** `An` int with the current value.


- **set** (`int Value`)

	> Sets the value.

	- **@p** `Value` is an int with the value to set.
	- **@r** `this` object


- **incrementAndGet** ()

	> Atomically adds one and returns the result.

	- **@r** `An` int with the updated value.


- **decrementAndGet** ()

	> Atomically subtracts one and returns the result.

	- **@r** `An` int with the updated value.


- **addAndGet** (`int Delta`)

	> Atomically adds Delta and returns the result.

	- **@p** `Delta` is an int with the amount to add (may be negative).
	- **@r** `An` int with the updated value.


- **getAndSet** (`int Value`)

	> Atomically sets Value and returns the previous value.

	- **@p** `Value` is an int with the value to set.
	- **@r** `An` int with the previous value.


- **compareAndSet** (`int Expect, int Update`)

	> Atomically sets Update only if the current value equals Expect.

	- **@p** `Expect` is an int with the expected current value.
	- **@p** `Update` is an int with the new value.
	- **@r** `A` bool with true when the swap happened.




## class: Latch

[355:14] (extern: com.aussom.stdlib.ALatch) **extends: object** 

One-shot countdown gate. One thread waits until N events have
happened: N workers finished, N messages arrived. The count
cannot be reset; create a new Latch for a new round. await always
takes a timeout so a script cannot park a host worker thread
forever.

#### Methods

- **Latch** (`int Count = 1`)

	> Creates a new Latch.

	- **@p** `Count` is an optional int with the number of countDown calls that release waiters. Default 1, a one-shot gate.
	- **@r** `A` new Latch object.


- **newLatch** (`int Count = 1`)


- **countDown** ()

	> Counts down by one. At zero all waiters are released.

	- **@r** `this` object


- **await** (`int TimeoutMs`)

	> Waits until the count reaches zero or the timeout passes.

	- **@p** `TimeoutMs` is an int with how long to wait in milliseconds.
	- **@r** `A` bool with true when the count reached zero in time.


- **getCount** ()

	> Gets the remaining count.

	- **@r** `An` int with the remaining count.




## class: AtomicRef

[154:14] (extern: com.aussom.stdlib.AAtomicRef) **extends: object** 

Atomic reference holding any Aussom value. The use case is
whole-value publication: build a new map or list, then set() it
in one step so readers see the old value or the new one, never a
mix. Each method call is atomic; a sequence of method calls is
not.

#### Methods

- **AtomicRef** (`Initial = null`)

	> Creates a new AtomicRef.

	- **@p** `Initial` is an optional starting value. Default null.
	- **@r** `A` new AtomicRef object.


- **newAtomicRef** (`Initial = null`)


- **get** ()

	> Gets the current value.

	- **@r** `The` current value, or null when unset.


- **set** (`Value`)

	> Sets the value.

	- **@p** `Value` is the value to store.
	- **@r** `this` object


- **getAndSet** (`Value`)

	> Atomically sets Value and returns the previous value.

	- **@p** `Value` is the value to store.
	- **@r** `The` previous value.


- **compareAndSet** (`Expect, Update`)

	> Atomically sets Update only if the current value IS Expect. Matches by reference identity (the same underlying object), except null matches null.

	- **@p** `Expect` is the expected current value.
	- **@p** `Update` is the new value.
	- **@r** `A` bool with true when the swap happened.




