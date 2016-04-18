include_defs('//VERSION')

DEPS = [
  '//blame-cache:lib',
  '//blame-cache:src',
  '//blame-cache:javadoc',
  '//gitiles-dev:dev',
  '//gitiles-servlet:servlet',
  '//gitiles-servlet:src',
  '//gitiles-servlet:javadoc',
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
  jar = {
    'blame-cache': '//blame-cache:lib',
    'gitiles-servlet': '//gitiles-servlet:servlet',
  },
  src = {
    'blame-cache': '//blame-cache:src',
    'gitiles-servlet': '//gitiles-servlet:src',
  },
  doc = {
    'blame-cache': '//blame-cache:javadoc',
    'gitiles-servlet': '//gitiles-servlet:javadoc',
  },
)

def b():
  a = set()
  for d in DEPS:
    n,t = d.split(':')
    q = "%s-%s" % (n[2:], t)
    a.add(q)
    out = "%s.jar" % q
    genrule(
      name = q,
      cmd = 'ln -s $(location %s) $OUT' % d,
      out = out,
    )

  zip_file(
    name = 'all',
    srcs = [':%s' % e for e in a],
  )

b()
