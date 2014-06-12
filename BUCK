include_defs('//VERSION')
include_defs('//bucklets/maven_package.bucklet')

DEPS = [
  '//gitiles-dev:dev',
  '//gitiles-servlet:servlet',
  '//gitiles-servlet:src',
  '//gitiles-servlet:javadoc',
  '//gitiles-war:gitiles',
]

java_library(
  name = 'classpath',
  deps = [
    '//gitiles-servlet:servlet',
    '//gitiles-servlet:servlet_tests',
    '//gitiles-dev:lib',
  ]
)

maven_package(
  repository = 'gerrit-maven-repository',
  url = 'gs://gerrit-maven',
  version = GITILES_VERSION,
  group = 'com.google.gitiles',
  jar = {'gitiles-servlet': '//gitiles-servlet:servlet'},
  src = {'gitiles-servlet': '//gitiles-servlet:src'},
  doc = {'gitiles-servlet': '//gitiles-servlet:javadoc'},
)

def b():
  a = set()
  for d in DEPS:
    n,t = d.split(':')
    a.add(t)
    out = "%s.%s" % (t, 'war' if 'war' in n else 'jar')
    genrule(
      name = t,
      cmd = 'ln -s $(location %s) $OUT' % d,
      out = out,
    )

  genrule(
    name = 'all',
    cmd = 'echo done >$OUT',
    deps = [':' + e for e in a],
    out = '__fake.gitiles__',
  )

b()
