Concurrent Systems a.y. 2026-2027 - ISI LM UNIBO - Cesena Campus

# Lab Activity #05 - 20261016

### Multithreaded programming in Java - Intro 

- Java Thread Basic API overview 
	- How to define / create / spawn threads
	- A first look at basic methods/mechanisms and Java System API  
	  - Example: `sisco.hello_threads`
- A first tool for profiling concurrent programs: **JConsole**
- "How many threads" example: `sisco.how_many_threads`
  - about the limitations that we have with platform (OS) threads in Java

### Multithreaded programming in Java: Performance, CPU vs. I/O bound programs

- On the performance benefit of multithreading - "concurrent sorting" case study
  - Consider the sequential program: `sisco.concur_sort` - it creates and sorts an array. Let's create a concurrent version exploiting 2 threads
     -  measure the speedup
   - Generalising the solution to scale with the number of cores
      - measuring speed-up with different configurations (1, 2, 4, #cores, >#cores)

- From a CPU-bound to an IO-bound example: `sisco.io_bound_test`
  - executing the test (and measuring performance) with different configurations
    - pure CPU (with 1,2, #cores, >#cores)
    - CPU + I/O (with 1,2, #cores, >#cores)

- "bouncing balls" example
  - when sleeping could be useful...

