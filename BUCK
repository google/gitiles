include_defs('//VERSION')

DEPS = [
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
  jar = {'gitiles-servlet': '//gitiles-servlet:servlet'},
  src = {'gitiles-servlet': '//gitiles-servlet:src'},
  doc = {'gitiles-servlet': '//gitiles-servlet:javadoc'},
)

def b():
  a = set()
  for d in DEPS:
    n,t = d.split(':')
    a.add(t)
    out = "%s.jar" % t
    genrule(
      name = t,
      cmd = 'ln -s $(location %s) $OUT' % d,
      out = out,
    )

  zip_file(
    name = 'all',
    srcs = [':%s' % e for e in a],
  )

b()
