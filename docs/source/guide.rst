============
 User guide
============

.. highlight:: clojure

Creating tasks
==============

To create a task, use ``run``. It is a macro that evaluates its result in another thread. The
execution is done in the ``ForkJoinPool.commonPool()``::

   (task/run (println "hello")
             (Thread/sleep 1000)
             123)

To get the value of the task, use ``deref`` or ``@`` from the Clojure standard library::

   @(task/run 123)

Note, that calling ``(run 123)`` results possibly in the creation of another thread. To create an
"immeadiate" value use ``now``::

   @(task/now 123)

To create an empty future you can use ``(void)`` which is a task that never completes.

If you want to use another executor, use ``run-in``::

  (let [pool (Executors/newFixedThreadPool 16)]
    (task/run-in pool
      (Thread/sleep 1000)
      123)

Composing tasks
===============

Applying functions
------------------

If you want to apply a function on the value returned by a task, use ``then``::

  (task/then clojure.string/upper-case (task/run "asdf"))

``then`` produces another task, so you can deref its result:::

  @(task/then inc (task/run 123))

  ; => 124

If you want to apply a function that produces another task, use ``compose``::

  @(task/compose (fn [x] (task/run (Thread/sleep 1212) (inc x)))) (task/run 778))

If you had used ``then`` the result would have been a task inside another task.

.. _interoperability:

Interoperability
----------------

Standard Clojure ``future`` and ``promise`` are compatible with tasks::

  @(then inc (future 9)) ; => 10

  (def foo (promise))

  (then println foo)

  (deliver foo "hello")

  ; prints "hello"

Using executors
~~~~~~~~~~~~~~~

Both ``then`` and ``compose`` accept a third parameter as the `ExecutorService`_:::

  (let [pool (Executors/newFixedThreadPool 4)]
    (task/then inc (task/run 123) pool))

.. _ExecutorService: https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html

Using ``for`` to avoid boilerplate
----------------------------------

The ``for`` macro, like its standard library namesake, lets you skip some boilerplate when working
with tasks that depend on each other::

  (task/for [x (task/run 123)
             y (future 123)
             z (task/run (Thread/sleep 1000) 4)]
    (+ x y z))

``for`` evaluates its body once all futures are complete, and the values of each future are bound to
their respective bindings.

Lists of tasks
--------------

If you have a sequence of futures, and you want to deal with each result as a whole, you can turn
a sequence of tasks into a task that evaluates into the values of each task inside it::

  @(task/sequence [(task/run 123) (future 9) (task/run (Thread/sleep 100) "hello")])

  ; => [123 9 "hello"]


Completion
==========

To check if a task is complete, use ``done?``::

  (task/done? some-task)
  
  (task/done? (task/run (Thread/sleep 123123) 'foo))
  ; => false

  (task/done? (task/now 1))

To complete a task before it is done, use ``complete!``::

  (def baz (task/run (Thread/sleep 10000) 'foo))

  (task/complete! baz 'bar)

  @baz ; => 'bar

If the task is already complete, it does nothing.

To get a value anyway if the task isn't complete, use ``else``::

  (else (task/run (Thread/sleep 1000) 1) 2)

  ; => 2

.. _force:

Forcing a result
~~~~~~~~~~~~~~~~

To force the result of a task, completed or not, use ``force!``::

  (def t (task/now 123))

  (task/force! t 'hi)

  @t ; => 'hi

.. _cancellation:

Cancellation
============

To cancel a task, use ``cancel``::

  (def my-task (task/run (Thread/sleep 10000) 'bla))

  (task/cancel my-task)

To see if the task was cancelled, use ``cancelled?``::

  (cancelled? my-task) ; => true

Using ``deref`` on a cancelled task blows up, predictably.

Failures
========

A task is said to have *failed* if its evaluation produced an exception or it produced an exception
during its execution. Such a task is a cancelled task (see :ref:`cancellation`), or any task that
produces an exception when ``deref``'d::

  (def oops (task/run (throw (RuntimeException. "hey!"))))

  @oops

  ; RuntimeException hey!  task.core/fn--17494 (form-init7142405608168193525.clj:182)

``failed?`` will tell you if that task has failed::

  (task/failed? oops) ; => true

To create a failed task with some exception, use ``failed``::

  (def failed-task (task/failed (RuntimeException. "argf")))

To get the exception that caused the failure, use ``failure``::

  (task/failure failed-task) ; => RuntimeException[:cause "argf"] 

To force a task to fail, like :ref:`force! <force>`, use ``fail!``::

  (def foo (task/now "hi there"))

  (task/fail! foo (IllegalStateException. "poop"))

  (task/failed? foo) ; => true

  (task/failure foo) ; => IllegalStateException[:cause "poop"]

Chaining a failed task to a normal task will cause the resulting task to fail.

Recovering from errors
======================

To recover from errors, use ``recover``::

  (def boom (task/run (/ 1 0)))

  (def incremented (task/then inc boom))

This will blow up, so we can ensure that the resulting operation succeeds::

  @(recover incremented
            (fn [ex]
              (println "caught exception: " (.getMessage ex))
              123))

  ; caught exception: java.lang.ArithmeticException: Divide by zero
  ; => 123

So you can recover from potential failures using a backup value from the function. Supplying a
non-function will just recover with that value::

  @(recover boom "hello") ; => "hello"


