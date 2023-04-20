/* Akshata Dhuraji, c3309266
Operating System Assignment3

A3 class has 2 main components CacheManager class to manage the frames and ProcessManager class to manage the pages

CacheManager class
Description : Manages frames, keeps track of pages loaded in the frame
Precondition : Data from input file is read and passed to the CacheManager classs
Postcondition: loads  and removes the processes from the frames/main memory

int pageStatus(int page)
Description : Checks the frame to confirm if the page exist in the main memory

void accessPage(int page)
Description : stores the page passed by process manager into the main memory/frame

void swapPage(int page)
Description :Swaps the page in the main memory/frame

void oneCycle()
Description : simulates one time slice run

ProcessManager class
Description : keeps track of process execution scheduled using roundrobin
Precondition : data from input file is read and passed to ProcessManager, CacheManager class is declared to manage the frames/main memory
Postcondition: executes processses and records the fault details, turnaround time,  and total number of faults for each process.

int tryExecute(int type)
Description :simulates the execution attempt of the current process, records the fault numbers and turnaround time

boolean allDone()
Description : checks if all the processes are done and return boolean value

void oneCycle()
Description : executes the processes using CacheManager.oneCycle

*/
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class A3 {

    public static final int CACHE_LRU = 1;
    public static final int CACHE_CLOCK = 2;

    public static final int STATUS_UNCACHED = 1;
    public static final int STATUS_SWAPPING = 2; //tracks the page contains the key in the cache
    public static final int STATUS_LOADED = 3;   //tracks if the page is already available in the main memory

    //public static final int EXECUTION_PROCESS = 1;
    public static final int EXECUTION_CANDIDATE = 2;

    public static final int FAILURE_NONE = 0;
    public static final int FAILURE_SLICES = 1;
    public static final int FAILURE_CACHE = 2;
    public static final int FAILURE_INSTRUCTIONS = 3;

    // As per assignment specification
    public static final int MAXIMUM_PAGES = 50;

    private static class CacheManager {
        HashSet<Integer> pages;
        ConcurrentHashMap<Integer, Integer> ages;
        ConcurrentHashMap<Integer, Integer> swaps;
        ConcurrentHashMap<Integer, ArrayList<Integer>> faults;

        int limit;//Number of frames as shared by user during run time.
        int time;
        int cache;
        //Constructor
        public CacheManager(int limit, int cache) {
            this.limit = limit;
            this.cache = cache;

            this.pages = new HashSet<Integer>(2 * limit);
            this.ages = new ConcurrentHashMap<Integer, Integer>(2 * limit);
            this.swaps = new ConcurrentHashMap<Integer, Integer>(2 * limit);

            this.time = 0;
        }
        //returns a value to confirm if the page is present in the main memory and contains the key
        int pageStatus(int page) {

            if (swaps.containsKey(page)) {//checks if the page contains the key in the cache
                return STATUS_SWAPPING;
            }

            if (pages.contains(page)) { //checks if the page is already available in the main memory
                return STATUS_LOADED;
            }

            return STATUS_UNCACHED;

        }

        void accessPage(int page) {

            if (cache == CACHE_LRU) {
                ages.put(page, time);
            }

            // do nothing if CACHE_CLOCK

        }

        void swapPage(int page) {

            swaps.put(page, 6); // one page requires six units of time (cycles) for being swapped.
            ages.put(page, time); // sets the age for the page

        }

        // simulates one time slice run
        void oneCycle() {

            for (Map.Entry<Integer,Integer> swap : swaps.entrySet()) {

                int page = swap.getKey();
                int cycles = swap.getValue();

                int remainingCycles = cycles - 1; //decrememnts the cycles

                if (remainingCycles == 0) {

                    swaps.remove(page); //removes the page from main memory

                    if (pages.size() < limit) { //if the number of page size is less than  the number of frames

                        pages.add(page); //adds the page in the main memory

                    } else {

                        int processA = page / MAXIMUM_PAGES;

                        int oldestPage = -1;
                        int oldestAge = time;

                        for (int oldPage : pages) {

                            int processB = oldPage / MAXIMUM_PAGES;

                            // Do not remove a page from an unrelated process
                            if (processA != processB) {
                                continue;
                            }

                            int oldAge = ages.get(oldPage);

                            if (oldAge < oldestAge) {
                                oldestPage = oldPage;
                                oldestAge = oldAge;
                            }

                        }

                        pages.remove(oldestPage); // remove the other page, freeing space
                        pages.add(page); // adds the new page
                        ages.put(page, time); // sets the age for the page

                    }

                } else {

                    swaps.put(page, remainingCycles);

                }

            }

            time += 1;

        }

    }

    private static class ProcessManager {
        CacheManager cache; //Object of CacheManager class
        ArrayList<Integer>[] processes; //Reads processes
        ArrayList<Integer>[] faults; // stores all page faults for each process
        ConcurrentHashMap<Integer, Integer> indexes; // stores at which index each process is
        ConcurrentHashMap<Integer, Integer> turnarounds; // stores how long each process has lived

        int roundRobin; // quantum size for this round-robin scheduler
        int candidate; // which process should be picked next
        int failure; // reason for last failure
        int success; // last process that has succeded
        int process;
        int slices; // how many time slices the current process has
        int time; // the time now

        boolean firstRun;
        //constructor
        public ProcessManager(CacheManager cache, ArrayList<Integer>[] processes, int roundRobin) {
            this.processes = processes;
            this.roundRobin = roundRobin;

            this.candidate = 0;
            this.time = 0;

            this.failure = 0;
            this.success = 0;
            this.process = 0;
            this.slices = roundRobin;

            this.indexes = new ConcurrentHashMap<Integer, Integer>();
            this.turnarounds = new ConcurrentHashMap<Integer, Integer>();
            this.faults = new ArrayList[processes.length];

            for (int i = 0; i < processes.length; i += 1) {
                this.indexes.put(i, 0);
                this.turnarounds.put(i, -1);
                this.faults[i] = new ArrayList<Integer>();
            }

            this.cache = cache;

            this.firstRun = true;
        }

        // simulates the execution attempt of the current process
        int tryExecute(int type) {

            // no time slices left to run the program
            if (slices == 0) {
                failure = FAILURE_SLICES;
                return FAILURE_SLICES;
            }

            ArrayList<Integer> instructions = processes[process];

            int index = indexes.get(process);

            // no instructions left to execute
            if (index == instructions.size()) {
                slices = 0;
                failure = FAILURE_INSTRUCTIONS;
                return FAILURE_INSTRUCTIONS;
            }

            int localPageNumber = instructions.get(index);
            int globalPageNumber = MAXIMUM_PAGES * process + localPageNumber;

            cache.accessPage(globalPageNumber);

            int status = cache.pageStatus(globalPageNumber);

            if (status == STATUS_SWAPPING || status == STATUS_UNCACHED) {

                if (status == STATUS_UNCACHED) {

                    ArrayList<Integer> processFaults = faults[process];
                    processFaults.add(time);

                    cache.swapPage(globalPageNumber);
                }

                slices = 0;
                failure = FAILURE_CACHE;
                return FAILURE_CACHE;

            }

            int nextIndex = index + 1;
            indexes.put(process, nextIndex);

            if (nextIndex == instructions.size()) {

                int turnaround = turnarounds.get(process);

                if (turnaround < 0) {
                    turnarounds.put(process, time + 1);
                }

            }

            slices -= 1;

            success = process;

            failure = FAILURE_NONE;
            return FAILURE_NONE;

        }

     /*   boolean isDone(int process) { //Debug code

            int index = indexes.get(process);

            ArrayList<Integer> instructions = processes[process];

            return index == instructions.size();

        }*/

        //checks if the process execution is completed
        boolean allDone() {

            boolean complete;
            complete = true;

            for (Map.Entry<Integer,Integer> entry : indexes.entrySet()) {

                int process = entry.getKey();
                int index = entry.getValue();

                ArrayList<Integer> instructions = processes[process];

                if (index != instructions.size()) {
                    complete = false;
                    break;
                }

            }

            return complete;

        }

        void oneCycle() {

            cache.oneCycle();

            boolean hasExecuted;
            hasExecuted = false;

            int failureReason;
            failureReason = FAILURE_NONE;

            int tries = 0;
            hasExecuted = false;

            candidate = success;

            while (tries < processes.length) {

                process = candidate;

                if (slices == 0) {
                    slices = roundRobin;
                }

                failureReason = tryExecute(EXECUTION_CANDIDATE);

                if (slices == 0) {
                    success = (process + 1) % processes.length;
                }

                hasExecuted = failureReason == FAILURE_NONE;

                if (hasExecuted) {
                    break;
                }

                candidate = (candidate + 1) % processes.length;
                tries += 1;

            }

            firstRun = false;
            time += 1;

        }

        void runSimulation() {

            // while there is any process with instructions left
            while (!allDone()) {
                oneCycle();
            }

        }

    }

    private static final int PARSER_BEGIN = 1;
    private static final int PARSER_INTEGER = 2;
    private static final int PARSER_END = 3;

    private static ArrayList<Integer> parseFile(String fileName) {

        ArrayList<Integer> process = new ArrayList<Integer>();

        if (process == null) {
            System.err.format("PARSING FILE %s FAILED!\r\n", fileName);
            System.exit(0);
        }

        int state = PARSER_BEGIN;
        boolean done = false;

        Scanner scanner = null;

        try {

            scanner = new Scanner(new File(fileName));//Reads text file provided by user

            while (!done) {

                String word = scanner.next("\\S+");

                switch (state) {

                    case PARSER_BEGIN: {

                        if (!word.equals("begin")) {
                            throw new Exception("Input must begin with 'begin'!");
                        }

                        state = PARSER_INTEGER;
                        break;

                    }

                    case PARSER_INTEGER: {

                        if (word.equals("end")) {
                            state = PARSER_END;
                            done = true;
                            continue;
                        }

                        int integer = Integer.parseInt(word);
                        process.add(integer);

                    }

                }

            }

        } catch (Exception exception) {

            System.err.println("Error! Bad input file!");
            exception.printStackTrace(System.err);
            System.exit(0);

        } finally {

            scanner.close();

        }

        return process;
    }

    public static void main(String[] arguments) {

        if (arguments.length < 3) { //checks for the number of arguments
            System.err.println("Usage: PROGRAM <FRAMES_COUNT> <QUANTUM_SIZE> INPUT1.TXT INPUT2.TXT ...");
            System.exit(0);
        }
        //initialise variables to read the number of frames and quantum based on user input
        int numberOfFrames = 0;
        int quantumSize = 0;

        try {

            numberOfFrames = Integer.parseInt(arguments[0]);
            quantumSize = Integer.parseInt(arguments[1]);

        } catch (NumberFormatException exception) {

            System.err.println("Invalid input! Not a number!");
            System.exit(0);

        }

        int count = arguments.length - 2;

        ArrayList<Integer>[] processes; //Arraylist to store the process files from user input
        processes = new ArrayList[count];

        String[] fileNames;
        fileNames = new String[count];

        for (int i = 2; i < arguments.length; i += 1) {
            processes[i - 2] = parseFile(arguments[i]);	//reads the process names provided by user at runtime
            fileNames[i - 2] = arguments[i];
        }//filenames are read from the user inputs

        CacheManager leastRecentlyUsed, clock;	//Create objects of class CacheManager
        leastRecentlyUsed = new CacheManager(numberOfFrames, CACHE_LRU);//Calls the constructor
        clock = new CacheManager(numberOfFrames, CACHE_CLOCK);//Calls the constructor

        ProcessManager managerA, managerB;
        managerA = new ProcessManager(leastRecentlyUsed, processes, quantumSize);//Calls the constructor
        managerB = new ProcessManager(clock, processes, quantumSize);//Calls the constructor

        managerA.runSimulation();
        managerB.runSimulation();

        // outputting the results of the simulations.

        System.out.println("LRU - Fixed:");
        System.out.println("PID  Process Name      Turnaround Time  # Faults  Fault Times  ");

        for (int i = 0; i < managerA.processes.length; i += 1) {

            ArrayList<Integer> faults = managerA.faults[i];
            String fileName = fileNames[i];

            int process = i + 1;
            int turnaround = managerA.turnarounds.get(i);
            int faultsNumber = faults.size();

            System.out.format(
                    "%-5d%-18s%-17d%-10d",
                    process,
                    fileName,
                    turnaround,
                    faultsNumber
            );

            System.out.print("{");

            if (faults.size() >= 1) {

                int firstFault = faults.get(0);

                System.out.format("%d", firstFault);

            }

            for (int j = 1; j < faults.size(); j += 1) {

                int fault = faults.get(j);
                System.out.format(", %d", fault);

            }

            System.out.println("}");

        }

        System.out.println();

        System.out.println("------------------------------------------------------------");

        System.out.println("Clock - Fixed:");
        System.out.println("PID  Process Name      Turnaround Time  # Faults  Fault Times  ");

        for (int i = 0; i < managerB.processes.length; i += 1) {

            ArrayList<Integer> faults = managerB.faults[i];
            String fileName = fileNames[i];

            int process = i + 1;
            int turnaround = managerB.turnarounds.get(i);
            int faultsNumber = faults.size();

            System.out.format(
                    "%-5d%-18s%-17d%-10d",
                    process,
                    fileName,
                    turnaround,
                    faultsNumber
            );

            System.out.print("{");

            if (faults.size() >= 1) {

                int firstFault = faults.get(0);

                System.out.format("%d", firstFault);

            }

            for (int j = 1; j < faults.size(); j += 1) {//prints faults for the respective process

                int fault = faults.get(j);
                System.out.format(", %d", fault);

            }

            System.out.println("}");

        }


        System.out.println();

    }

}
