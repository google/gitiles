Gitiles - A simple JGit repository browser
==========================================

Gitiles is a simple repository browser for Git repositories, built on JGit. Its
guiding principle is simplicity: it has no formal access controls, no write
access, no fancy Javascript, etc.


Building
--------

Gitiles requires [Buck](http://facebook.github.io/buck/) to build.

```
  sudo apt-get install ant
  cd ${HOME}
  git clone https://github.com/facebook/buck.git
  cd buck
  ant
  sudo ln -s ${PWD}/bin/buck /usr/bin/buck
  cd /path/to/gitiles
  git submodule update --init
  buck build all
  buck test
```


Testing
-------

```
  cd /path/to/repositories  # Don't run from the gitiles repo.
  /path/to/gitiles/tools/run_dev.sh
```

This will recompile and start a development server.  Open
http://localhost:8080/ to view your local copy of gitiles, which
will serve any repositories under `/path/to/repositories`.

To run unit tests, run `buck test`.


Eclipse IDE
-----------

If you'd like to use Eclipse to edit Gitiles, first generate a project file:

```
  ./bucklets/tools/eclipse.py --src
```

Import the project in Eclipse:

```
  File -> Import -> Existing Projects into Workpace
```

The project only needs to be rebuilt if the source roots or third-party
libraries have changed. For best results, ensure the project is closed in
Eclipse before rebuilding.


Code Style
----------

Java code in Gitiles follows the [Google Java Style Guide]
(https://google.github.io/styleguide/javaguide.html) with a 100-column limit.

CSS in Gitiles follows the [SUIT CSS naming conventions]
(https://github.com/suitcss/suit/blob/master/doc/naming-conventions.md).

Code Review
-----------

Gitiles uses Gerrit for code review:
https://gerrit-review.googlesource.com/

Gitiles uses the ["git push" workflow][1] with server
https://gerrit.googlesource.com/gitiles.  You will need a
[generated cookie][2].

[1]: https://gerrit-review.googlesource.com/Documentation/user-upload.html#_git_push
[2]: https://gerrit.googlesource.com/new-password

Gerrit depends on "Change-Id" annotations in your commit message.
If you try to push a commit without one, it will explain how to
install the proper git-hook:

```
curl -Lo `git rev-parse --git-dir`/hooks/commit-msg \
    https://gerrit-review.googlesource.com/tools/hooks/commit-msg
chmod +x `git rev-parse --git-dir`/hooks/commit-msg
```

Before you create your local commit (which you'll push to Gerrit)
you will need to set your email to match your Gerrit account:

```
git config --local --add user.email foo@bar.com
```

Normally you will create code reviews by pushing for master:

```
git push origin HEAD:refs/for/master
```
