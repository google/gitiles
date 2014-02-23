include_defs('//VERSION')
include_defs('//bucklets/maven_package.bucklet')
TYPE = 'snapshot' if GITILES_VERSION.endswith('-SNAPSHOT') else 'release'

DEPS = [
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
    '//gitiles-dev:dev',
  ]
)

maven_package(
  repository = 'gerrit-api-repository',
  url = 'gs://gerrit-api/%s' % TYPE,
  version = GITILES_VERSION,
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
      deps = [d],
      out = out,
    )

  genrule(
    name = 'all',
    cmd = 'echo done >$OUT',
    deps = [':' + e for e in a],
    out = '__fake.gitiles__',
  )

b()
