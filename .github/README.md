## Setup Instructions

There are two setup options available—please choose one based on your needs:

### 🔹 AAPS-CI Option 1 – Generate JKS  
[Watch Tutorial (YouTube Shorts)](https://youtube.com/shorts/Z8aSEXFqBBc?feature=share)

### 🔹 AAPS-CI Option 2 – Upload Existing JKS  
[Watch Tutorial (YouTube)](https://youtu.be/7RdCEGhG0zo)

### 🔗 Support Website  
[aaps-ci-preparation](https://github.com/Angus-repo/aaps-ci-preparation/releases/latest)

---

## Testing a Pull Request (PR)

If you want to test the code from a pull request, please follow the steps below and switch to using **PR-CI**:

### ▶️ AAPS PR-CI  
[Watch Tutorial (YouTube Shorts)](https://youtube.com/shorts/tmdcXvgaHfU?feature=share)

### PR Reference Types

- **`head`**  
  Fetches the actual content from the PR author’s branch (i.e., the original commit history without any merge operations).  
  This is equivalent to the original state of the PR branch, as if it were fetched directly from a fork or feature branch.

- **`merge`**  
  Fetches the result of GitHub’s pre-simulated merge of the PR into the target branch (e.g., `dev`).  
  This is a virtual merge commit automatically created by GitHub.  
  This commit only exists when the PR has no conflicts and is mergeable.

---

## Build the AAPS APK

Once the above setup steps are completed, you can run the build from **GitHub Actions**:

### ▶️ AAPS-CI Workflow for Building the AAPS APK  
[Watch Tutorial (YouTube Shorts)](https://youtube.com/shorts/7en4SF9bt-E?feature=share)