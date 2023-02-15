package gitlet;



import java.io.File;
import java.io.IOException;
import java.util.*;

import static gitlet.Utils.*;



/** Represents a gitlet repository.
 *
 *  does at a high level.
 *
 *  @author Quanzhi
 */
public class Repository {
    /**
     *
     *
     * List all instance variables of the Repository class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided two examples for you.
     */

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    public static final File OBJECTS_DIR = join(GITLET_DIR, "objects");
    public static final File COMMITS_DIR = join(GITLET_DIR, "commits");
    public static final File STAGED_DIR = join(GITLET_DIR, "stagedObj");


    // copy the given file from source directory to target directory
    private static void copyFileTo(String fName, File sourceDir, String tName, File targetDir) {
        File sourceFile = join(sourceDir, fName);
        if (!sourceFile.exists()) {
            printExit("source file not exist");
        }
        File targetFile = join(targetDir, tName);
        try {
            if (!targetFile.exists()) {
                targetFile.createNewFile();
            }
            writeContents(targetFile, readContents(sourceFile));
        } catch (IOException e) {
            printExit("fail to create the file");
        }
    }
    // return the message should be put in the conflicted file
    // in format
    /*
        <<<<<<< HEAD
        contents of file in current branch
        =======
        contents of file in given branch
        >>>>>>>
     */
    private static String getConfMsg(String headHash, String otherHash) {
        String headString = "";
        String otherString = "";
        if (headHash != null) {
            File headFile = join(OBJECTS_DIR, headHash);
            headString = readContentsAsString(headFile);
        }
        if (otherHash != null) {
            File otherFile = join(OBJECTS_DIR, otherHash);
            otherString = readContentsAsString(otherFile);
        }

        String res = "<<<<<<< HEAD\n" + headString + "=======\n" + otherString + ">>>>>>>\n";
        return res;
    }
    // write conflict message to the file in CWD
    private static void writeConf(String content, String fileName) {
        File targetFile = join(CWD, fileName);
        try {
            if (!targetFile.exists()) {
                targetFile.createNewFile();
            }
            writeContents(targetFile, content);
        } catch (IOException e) {
            printExit("fail to create new file");
        }
    }
    // handling conflicts for a specific file
    private static void handleConf(String headHash, String otherHash, String file) {
        System.out.println("Encountered a merge conflict.");
        //conflict case
        String confMsg = getConfMsg(headHash, otherHash);
        writeConf(confMsg, file);
        // staged file for change
        add(file);
    }
    private static void confMerge(boolean inSplit, boolean inOther, boolean inHead,
                             String otherHash, String splitHash, String headHash,
                             String file) {
        // both modified
        if (inSplit && inOther && inHead
                && !splitHash.equals(headHash)
                && !headHash.equals(otherHash)
                && !otherHash.equals(splitHash)) {
            handleConf(headHash, otherHash, file);
        }
        // both add but different content
        if (!inSplit && inOther && inHead && !otherHash.equals(headHash)) {
            // conflict case
            handleConf(headHash, otherHash, file);
        }
        // other modified head removed
        if (inSplit && inOther && !inHead && !otherHash.equals(splitHash)) {
            // conflict case
            handleConf(headHash, otherHash, file);
        }
        // head modified other rmeoved
        if (inSplit && !inOther && inHead && !headHash.equals(splitHash)) {
            // conflict case
//                System.out.println("should be this!!");
            handleConf(headHash, otherHash, file);
        }
    }
    // add a file to staged and write back to dir
    private  static void addToStaged(String key, String value, File sourceDir) {
        // copy file to map
        HashMap<String, String> staged = getStaged();
        staged.put(key, value);
        writeObject(join(GITLET_DIR, "STAGED"), staged);
        // copy file to staged file
        copyFileTo(value, sourceDir, value, STAGED_DIR);
    }
    // validate if the repo initialized
    private static void validateRepoExisted() {
        if (!GITLET_DIR.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
    }
    // get staged map from the file system
    private static HashMap<String, String> getStaged() {
        File stagedPath = join(GITLET_DIR, "STAGED");
        HashMap<String, String> staged = readObject(stagedPath,
                                                    (new HashMap<String, String>()).getClass());
        return staged;
    }
    // get branches
    private static HashMap<String, String> getBranch() {
        File branchesPath = join(GITLET_DIR, "BRANCHES");
        HashMap<String, String> branch = readObject(branchesPath,
                                                    (new HashMap<String, String>()).getClass());
        return branch;
    }
    // clear CWD and copy all files from the given commit to CWD
    private static void setCommit(Commit commit) {
        HashMap<String, String> staged = getStaged();
        HashMap<String, String> branches = getBranch();
        // delete all files in staged
        for (String file : plainFilenamesIn(STAGED_DIR)) {
            join(STAGED_DIR, file).delete();
        }
        staged.clear();
        writeObject(join(GITLET_DIR, "STAGED"), staged);
        Commit curr = getCommit(getHead());
        // delete all files in current commit
        for (String file : plainFilenamesIn(CWD)) {
            if (curr.files.containsKey(file)) {
                join(CWD, file).delete();
            }
        }

        // copy all files from branch needed

        for (String key :commit.files.keySet()) {
            try {
                File newFile = join(CWD, key);
                newFile.createNewFile();
                byte[] newContent = readContents(join(OBJECTS_DIR, commit.files.get(key)));
                writeContents(newFile, newContent);
            } catch (IOException e) {
                System.err.println(e);
            }
        }
    }

    private static void printCommit(String commitHash, Commit commit) {

        System.out.println("===");
        System.out.println("commit " + commitHash);
        if (commit.secondParent != null) {
            System.out.println("Merge: "
                    + commit.parent.substring(0, 7) + " " + commit.secondParent.substring(0, 7));
        }
        System.out.println(String.format("Date: %1$ta %1$tb %1$te %1$tT %1$tY %1$tz",
                                            commit.date));
        System.out.println(commit.message);
        System.out.println();
        return;
    }
    // get a commit object by its name
    private static void forwardCommit(String msg, String secondParent) {
        // create a new commit
        HashMap<String, String> staged = getStaged();
        String prevHead = getHead();
        Commit prevCommit = getCommit(prevHead);
        Commit newCommit = new Commit(prevCommit);
        newCommit.parent = prevHead;
        newCommit.secondParent = secondParent;
        newCommit.message = msg;

        // copy staged file into the commit
        for (Map.Entry<String, String> entry : staged.entrySet()) {
            // if the entry is NULL, indicate that file deleted, remove pair from the commit
            if (entry.getValue() == null) {
                newCommit.files.remove(entry.getKey());
                continue;
            }
            // put name sha1 pair into commit
            newCommit.files.put(entry.getKey(), entry.getValue());
            // copy files into objects dir
            File copiedFile = join(OBJECTS_DIR, entry.getValue());
            try {
                copiedFile.createNewFile();
            } catch (IOException e) {
                System.err.println(e);
                System.exit(0);
            }
            File stagedFile = join(STAGED_DIR, entry.getValue());
            byte[] stagedContent = readContents(stagedFile);
            writeContents(copiedFile, stagedContent);



        }
        // empty the staged Map and save back
        staged.clear();
        // empty staged area
        for (String file : plainFilenamesIn(STAGED_DIR)) {
            join(STAGED_DIR, file).delete();
        }

        writeObject(join(GITLET_DIR, "STAGED"), staged);
        // save the new commit
        String newSha1 = sha1(serialize(newCommit));
        writeObject(join(COMMITS_DIR, newSha1), newCommit);
        // update head
        String currentBranch = getHeadBranch();
        HashMap<String, String> branchMap = getBranch();
        writeContents(join(GITLET_DIR, "HEAD"), newSha1 + "\n" + currentBranch);
        // update branch
        branchMap.put(currentBranch, newSha1);
        writeObject(join(GITLET_DIR, "BRANCHES"), branchMap);
    }
    private static Commit getCommit(String commitName) {
        return readObject(join(COMMITS_DIR, commitName), (new Commit()).getClass());
    }
    // get the head pointer
    private static String getHead() {
        return readContentsAsString(join(GITLET_DIR, "HEAD")).split("\n")[0];
    }
    // get head branch
    private static String getHeadBranch() {
        return readContentsAsString(join(GITLET_DIR, "HEAD")).split("\n")[1];
    }
    // print elements of a String list line by line
    private static void printList(List<String> lst) {
        for (String s : lst) {
            System.out.println(s);
        }
    }
    // search for commit that start with commit Name
    private static ArrayList<String> searchCommitName(String commitName) {
        ArrayList<String> res = new ArrayList<>();
        for (String f : plainFilenamesIn(COMMITS_DIR)) {
            int idx = f.indexOf(commitName);
            if (idx == 0) {
                res.add(f);
            }
        }
        return res;
    }

    /**
     * initialize .git repo to save informations
     */
    public static void init() {
        // create repo structures
        if (!GITLET_DIR.exists()) {
            GITLET_DIR.mkdir();
            OBJECTS_DIR.mkdir();
            COMMITS_DIR.mkdir();
            STAGED_DIR.mkdir();
        } else {
            printExit("A Gitlet version-control system already exists in the current directory.");
        }
        // create the initial commit
        Date start = new Date(0);
        Commit init = new Commit("initial commit", null, start);
        try {
            String commitName = sha1(serialize(init));
            File initCommit = join(COMMITS_DIR, commitName);
            initCommit.createNewFile();
            writeObject(initCommit, init);
            // create head pointer
            File head = join(GITLET_DIR, "HEAD");
            head.createNewFile();
            // point head pointer to the initial Commit
            writeContents(head, commitName + "\nmaster");
            // initialize staged field
            HashMap<String, String> staged = new HashMap<>();
            File stagedFile = join(GITLET_DIR, "STAGED");
            stagedFile.createNewFile();
            writeObject(stagedFile, staged);
            // initialize branches
            HashMap<String, String> branches = new HashMap<>();
            // set master to initial commit
            branches.put("master", commitName);
            File branchFile = join(GITLET_DIR, "BRANCHES");
            writeObject(branchFile, branches);
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    public static void add(String file) {
        // validate if there is an initialized repo
        validateRepoExisted();
        if (file.substring(file.length() - 1).equals("/")
                || file.substring(file.length() - 1).equals("\\")) {
            file = file.substring(0, file.length() - 1);
        }
        // process file to be added
        File toAdd = join(CWD, file);
        if (!toAdd.exists()) {
            printExit("File does not exist.");
        }
        byte[] fileContent =  readContents(toAdd);
        // compute sha-1 for the toAdd file
        String fileSha1 = sha1(fileContent);


        // get staged area
        HashMap<String, String> staged = getStaged();
        // get current commit
        String head = getHead();
        Commit currCommit = getCommit(head);

        // check if the version is same as current commit
        // if not same/not in current commit or in current staged, add to staged
        if (currCommit.files.get(file) == null || !fileSha1.equals(currCommit.files.get(file))) {
//            && (staged.get(file) == null || !fileSha1.equals(staged.get(file)))
            // store the file into objects directory
            try {
                // if another version of file already in the staged area, replace it
                if (staged.get(file) != null && !staged.get(file).equals(fileSha1)) {
                    join(STAGED_DIR, staged.get(file)).delete();
                }
                File toAddInObj = join(STAGED_DIR, fileSha1);
                toAddInObj.createNewFile();
                writeContents(toAddInObj, fileContent);
                staged.put(file, fileSha1); // add file to be added to staged area
            } catch (IOException e) {
                System.err.println(e);
            }
            // else remove from the staged area / do not add
        } else {
            // if not in staged area, remove from stages and delete the corresponding file
            if (staged.get(file) != null) {
                File needDelete = join(STAGED_DIR, staged.get(file));
                needDelete.delete();
            }
            if (staged.containsKey(file)) {
                staged.remove(file);
            }
        }
        // write staged back to STAGED file
        writeObject(join(GITLET_DIR, "STAGED"), staged);
    }

    public static void commit(String msg) {
        // validate if there is an initialized repo
        validateRepoExisted();

        // check if the staged area is empty
        HashMap<String, String> staged = readObject(join(GITLET_DIR, "STAGED"),
                                            (new HashMap<String, String>()).getClass());
        if (staged.size() == 0) {
            System.out.println("No changes added to the commit.");
            System.exit(0);
        }
        if (msg.equals("")) {
            printExit("Please enter a commit message.");
        }
        forwardCommit(msg, null);
        return;
    }

    public static void rm(String rmFile) {
        // validate if the repo existed
        validateRepoExisted();

        // read staged and current commit
        HashMap<String, String> staged = getStaged();
        String head = getHead();
        Commit currentCommit = getCommit(head);
        // if file neigher in staged nor current commit
        if (!staged.containsKey(rmFile) && !currentCommit.files.containsKey(rmFile)) {
            printExit("No reason to remove the file.");
        }
        // if the file is staged, remove from staged and delete the corresponding file
        if (staged.get(rmFile) != null) {
            File fileToRemove = join(STAGED_DIR, staged.get(rmFile));
            fileToRemove.delete();
            staged.remove(rmFile);
        }
        // if the file is in the current commit, remove it from dir
        // and mark it as removal in staged map
        if (currentCommit.files.get(rmFile) != null) {
            // remove from current dir
            if (join(CWD, rmFile).exists()) {
                restrictedDelete(join(CWD, rmFile));
            }
            // marked as removed and save staged
            staged.put(rmFile, null);
        }
        // save staged
        writeObject(join(GITLET_DIR, "STAGED"), staged);
        return;
    }
    // print log out
    public static void log() {
        // validate if repo exist
        validateRepoExisted();
        // get current head and commit
        String nextPtr = getHead();

        // print commit information
        do {
            Commit commitPtr = getCommit(nextPtr);
            printCommit(nextPtr, commitPtr);
            nextPtr = commitPtr.parent;
        } while (nextPtr != null);
        return;
    }
    // print global log
    public static void logGlob() {
        validateRepoExisted();
        List<String> commitList = plainFilenamesIn(COMMITS_DIR);
        for (String commitName : commitList) {
            Commit curr = getCommit(commitName);
            printCommit(commitName, curr);
        }
        return;
    }
    // print ids with given commits messages
    public static void find(String msg) {
        validateRepoExisted();
        List<String> commitList = plainFilenamesIn(COMMITS_DIR);
        boolean existed = false;
        // iterate through all commits in the dir
        for (String commitName : commitList) {
            Commit curr = getCommit(commitName);
            if (curr.message.equals(msg)) {
                System.out.println(commitName);
                existed = true;
            }
        }
        if (!existed) {
            System.out.println("Found no commit with that message.");
        }
        return;
    }
    // print out current status
    public static void status() {
        List<String> branchList = new ArrayList<>();
        List<String> removedList = new ArrayList<>();
        List<String> stagedList = new ArrayList<>();
        List<String> untrackedList = new ArrayList<>();
        List<String> modList = new ArrayList<>();
        validateRepoExisted();
        // get staged and current commit
        HashMap<String, String> staged = getStaged();
        String head = getHead();
        Commit curr = getCommit(head);
        // prepare branches List
        HashMap<String, String> branches = getBranch();
        for (String b : branches.keySet()) {
            branchList.add(b);
        }

        // prepare staged and removeList
        for (String file : staged.keySet()) {
            if (staged.get(file) != null) {
                stagedList.add(file);
            } else {
                removedList.add(file);
            }
        }
        // prepare modList
        for (String file : curr.files.keySet()) {
            if (!join(CWD, file).exists() && !staged.containsKey(file)) {
                modList.add(file + "(delete)");
            } else if (join(CWD, file).exists()) {
                byte[] fileContent = readContents(join(CWD, file));
                String fileSha1 = sha1(fileContent);
                if (!curr.files.get(file).equals(fileSha1)) {
                    if (!staged.containsKey(file) || !staged.get(file).equals(fileSha1)) {
                        modList.add(file + "(modified)");
                    }
                }
            }
        }
        // prepare untrackedList
        for (String file : plainFilenamesIn(CWD)) {
            if (!curr.files.containsKey(file) && !staged.containsKey(file)) {
                untrackedList.add(file);
            }
        }
        Collections.sort(branchList);
        Collections.sort(removedList);
        Collections.sort(modList);
        Collections.sort(stagedList);
        Collections.sort(untrackedList);
        // print branches
        System.out.println("=== Branches ===");
        String headBranch = getHeadBranch();
        for (String b : branchList) {
            if (b.equals(headBranch)) {
                b = "*" + b;
            }
            System.out.println(b);
        }
        System.out.println();

        // print staged file
        System.out.println("=== Staged Files ===");
        printList(stagedList);
        System.out.println();
        // print removed file
        System.out.println("=== Removed Files ===");
        printList(removedList);
        System.out.println();
        // print modification not staged for commit
        System.out.println("=== Modifications Not Staged for Commit ===");
        printList(modList);
        System.out.println();
        // print untracked file (not in staged and current commit)
        System.out.println("=== Untracked Files ===");
        printList(untrackedList);
        System.out.println();
        return;
    }
    public static void checkoutBranch(String bName) {
        Commit curr = getCommit(getHead());
        String currBranch = getHeadBranch();
        HashMap<String, String> staged = getStaged();
        HashMap<String, String> branches = getBranch();
        if (currBranch == bName) {
            printExit("No need to checkout the current branch.");
        }
        if (branches.get(bName) == null) {
            printExit("No such branch exists.");
        }
        for (String file : plainFilenamesIn(CWD)) {
            if (!curr.files.containsKey(file) && !staged.containsKey(file)) {
                printExit("There is an untracked file in "
                       + "the way; delete it, or add and commit it first.");
            }
        }
        String newBranchHead = branches.get(bName);
        Commit newBranchCommit = getCommit(newBranchHead);
        setCommit(newBranchCommit);
        // update HEAD
        String newHeadInfo = newBranchHead + "\n" + bName;
        writeContents(join(GITLET_DIR, "HEAD"), newHeadInfo);
        return;
    }

    public static void checkoutFile(String fName) {
        String head = getHead();
        Commit commit = getCommit(head);
        if (!commit.files.containsKey(fName)) {
            printExit("File does not exist in that commit.");
        }
        try {
            byte[] cont = readContents(join(OBJECTS_DIR, commit.files.get(fName)));
            File newFile = join(CWD, fName);
            if (!newFile.exists()) {
                newFile.createNewFile();
            }
            writeContents(newFile, cont);
        } catch (IOException e) {
            System.err.println(e);
            System.exit(0);
        }
        return;
    }

    public static void checkoutCommitFile(String commitName, String fName) {
        // search for commitName
        String head = getHead();
        Commit commit = getCommit(head);
        if (commitName.length() < 40) {
            ArrayList<String> commitFull = searchCommitName(commitName);
            if (commitFull.size() == 1) {
                commit = getCommit(commitFull.get(0));
            } else if (commitFull.size() == 0) {
                System.out.println("No commit with that id exists.");
                System.exit(1);
            } else {
                System.out.println("The commit id ambiguous");
                System.exit(1);
            }
        } else if (commitName.length() == 40 && join(COMMITS_DIR, commitName).exists()) {
            commit = getCommit(commitName);
        } else {
            System.out.println("No commit with that id exists.");
            System.exit(1);
        }
        String fileName = commit.files.get(fName);
        if (fileName == null) {
            System.out.println("File does not exist in that commit.");
            System.exit(0);
        }
        byte[] fileContent = readContents(join(OBJECTS_DIR, fileName));
        File writeFile = join(CWD, fName);
        try {
            if (!writeFile.exists()) {
                writeFile.createNewFile();
            }
            writeContents(writeFile, fileContent);
        } catch (IOException e) {
            System.err.println(e);
            System.exit(0);
        }
        return;
    }

    public static void createBranch(String bName) {
        HashMap<String, String> branches = getBranch();
        if (branches.containsKey(bName)) {
            printExit("A branch with that name already exists.");
        }
        String currentHead = getHead();
        // set a new branch
        branches.put(bName, currentHead);
        writeObject(join(GITLET_DIR, "BRANCHES"), branches);
        return;

    }

    public static void removeBranch(String bName) {
        HashMap<String, String> branches = getBranch();
        String currentBranchName = getHeadBranch();
        if (!branches.containsKey(bName)) {
            printExit("A branch with that name does not exist.");
        }
        if (currentBranchName.equals(bName)) {
            printExit("Cannot remove the current branch.");
        }
        branches.remove(bName);
        writeObject(join(GITLET_DIR, "BRANCHES"), branches);

    }

    public static void reset(String commitName) {
        String head = getHead();
        String bName = getHeadBranch();
        Commit curr = getCommit(head);
        HashMap<String, String> branches = getBranch();
        HashMap<String, String> staged = getStaged();
        for (String file : plainFilenamesIn(CWD)) {
            if (!curr.files.containsKey(file) && !staged.containsKey(file)) {
                printExit("There is an untracked file in the way; "
                       + "delete it, or add and commit it first.");
            }
        }
        ArrayList<String> commits  = searchCommitName(commitName);
        if (commits.size() == 0) {
            printExit("No commit with that id exists.");
        } else if (commits.size() > 1) {
            printExit("Ambiguous commit id");
        }
        commitName = commits.get(0);
        Commit newBranchCommit = getCommit(commitName);
        setCommit(newBranchCommit);
        // update HEAD
        String newHeadInfo = commitName + "\n" + bName;
        writeContents(join(GITLET_DIR, "HEAD"), newHeadInfo);
        // update branch
        branches.put(bName, commitName);
        writeObject(join(GITLET_DIR, "BRANCHES"), branches);

    }
    // find the lowest ancestor of two branch.
    private static String findLowestAncestor(String branch1, String branch2) {
        HashSet<String> ancestors = new HashSet<>();
        HashMap<String, String> branches = getBranch();
        String ptr1 = branches.get(branch1);
        String ptr2 = branches.get(branch2);
        // find lowest ancestor
        while (ptr1 != null || ptr2 != null) {
            // if ptr in hashset, means that we found common ancestor

            if (ptr1 != null) {
                if (ancestors.contains(ptr1)) {
                    return ptr1;
                }
                ancestors.add(ptr1);
                Commit ptr1Commit = getCommit(ptr1);
                ptr1 = ptr1Commit.parent;
            }
            if (ptr2 != null) {
                if (ancestors.contains(ptr2)) {
                    return ptr2;
                }
                ancestors.add(ptr2);
                Commit ptr2Commit = getCommit(ptr2);
                ptr2 = ptr2Commit.parent;
            }
        }
        return null;
    }
    public static void merge(String bName) {
        String currentBranch = getHeadBranch();
        Commit curr = getCommit(getHead());
        HashMap<String, String> branches = getBranch();
        HashMap<String, String> staged = getStaged();
        if (staged.size() != 0) {
            printExit("You have uncommitted changes.");
        }
        if (bName.equals(currentBranch)) {
            printExit("Cannot merge a branch with itself.");
        }
        if (!branches.containsKey(bName)) {
            printExit("A branch with that name does not exist.");
        }
        // find the lowest ancestor
        String split = findLowestAncestor(bName, currentBranch);
        if (split.equals(branches.get(bName))) {
            printExit("Given branch is an ancestor of the current branch.");
        }
        if (split.equals(branches.get(currentBranch))) {
            System.out.println("Current branch fast-forwarded.");
            checkoutBranch(bName);
        }
        for (String file : plainFilenamesIn(CWD)) {
            if (!curr.files.containsKey(file) && !staged.containsKey(file)) {
                printExit("There is an untracked file in the way; "
                        + "delete it, or add and commit it first.");
            }
        }
        Commit splitCommit = getCommit(split);
        Commit headCommit = getCommit(getHead());
        Commit otherCommit = getCommit(branches.get(bName));
        // conduct 8 rules
        // iterate through all files in otherCommit and splitCommit
        // no need to iterate files in headCummit because if a file
        // present in head but not in split and other
        // we don't need to do anything with it
        //all files in otherCommit and splitCommit
        HashSet<String> allFiles = new HashSet<>();
        allFiles.addAll(splitCommit.files.keySet());
        allFiles.addAll(otherCommit.files.keySet());
        // iterate through all files
        for (String file : allFiles) {
            boolean inSplit = splitCommit.files.containsKey(file);
            boolean inOther = otherCommit.files.containsKey(file);
            boolean inHead = headCommit.files.containsKey(file);
            String splitHash = splitCommit.files.get(file);
            String otherHash = otherCommit.files.get(file);
            String headHash = headCommit.files.get(file);
            // implement 8 rules
            // if file in other, not in split and head (file added in other)
            if (inOther && !inSplit && !inHead) {
                // copy otherHash to CWD
                copyFileTo(otherHash, OBJECTS_DIR, file, CWD);
                // add file to staged
                add(file);
            }
            // if file modified in otherCommit, no change in head copy to CWD
            if (inSplit && inOther && inHead && headHash.equals(splitHash)
                    && !otherHash.equals(headHash)) {
                // copy otherHash to CWD
                copyFileTo(otherHash, OBJECTS_DIR, file, CWD);
                // add file to staged
                add(file);
            }
            // if file removed in other, no change in head, removed from CWD and un-track
            if (inSplit && !inOther && inHead && headHash.equals(splitHash)) {
                // delete from CWD
                // un-track (label as removed)
                rm(file);
            }
            // ############## conflict case #############
            confMerge(inSplit, inOther, inHead, otherHash, splitHash, headHash, file);
        }
        // Commit the Change
        forwardCommit("Merged " +  bName + " into "
                + currentBranch + ".", branches.get(bName));
        return;
    }
}

}
