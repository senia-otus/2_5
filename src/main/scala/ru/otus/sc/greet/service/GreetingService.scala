package ru.otus.sc.greet.service

import ru.otus.sc.greet.model.{GreetRequest, GreetResponse}

trait GreetingService {
  def greet(request: GreetRequest): GreetResponse
}
