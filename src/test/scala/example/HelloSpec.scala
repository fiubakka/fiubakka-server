package example

class HelloSpec extends munit.FunSuite {
  test("say hello") {
    assertEquals(HelloWolrd.greeting, "hello")
  }
}
