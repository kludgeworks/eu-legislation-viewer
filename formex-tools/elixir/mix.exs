defmodule FormexV4.MixProject do
  use Mix.Project

  def project do
    [
      app: :formex_v4,
      version: "0.1.0",
      elixir: "~> 1.15",
      start_permanent: Mix.env() == :prod,
      deps: deps()
    ]
  end

  def application do
    [
      extra_applications: [:logger, :crypto]
    ]
  end

  defp deps do
    [
      {:broadway, "~> 1.2.1"},
      {:req, "~> 0.4"},
      {:duckdbex, "~> 0.3"},
      {:saxy, "~> 1.5"},
      {:jason, "~> 1.4"},
      {:uuid, "~> 1.1"},
      {:progress_bar, "~> 3.0"},
      {:bypass, "~> 2.1", only: :test},
      {:credo, "~> 1.7", only: [:dev, :test], runtime: false}
    ]
  end
end
